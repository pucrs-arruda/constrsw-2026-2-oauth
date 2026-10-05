import { Injectable } from "@nestjs/common";
import { ConflictError, NotFoundError } from "../common/errors";
import { KeycloakAdminClient } from "../keycloak/keycloak-admin.client";
import { CreateRoleDto } from "./dto/create-role.dto";
import { PatchRoleDto } from "./dto/patch-role.dto";
import { UpdateRoleDto } from "./dto/update-role.dto";
import { RoleResponse } from "./interfaces/role-response.interface";

/**
 * Subset of the Keycloak realm-role representation we rely on.
 */
export interface KeycloakRole {
  id: string;
  name: string;
  description?: string;
  composite?: boolean;
  clientRole?: boolean;
  containerId?: string;
  attributes?: Record<string, string[]>;
}

/**
 * Realm roles have no native soft-delete, so logical deletion is modelled with
 * a role attribute (`deleted=["true"]`): the role is kept in Keycloak but hidden
 * from every read path of this API.
 */
const DELETED_ATTRIBUTE = "deleted";

/** Page size when listing a role's holders before stripping its mappings. */
const HOLDERS_PAGE_SIZE = 100;

@Injectable()
export class RolesService {
  // Track D speaks to the Admin API through the shared service-account client:
  // no own `fetch`/token — status → exception mapping lives in the client.
  constructor(private readonly admin: KeycloakAdminClient) {}

  async create(dto: CreateRoleDto): Promise<RoleResponse> {
    await this.writeRole(dto.name, () =>
      this.admin.post("/roles", {
        name: dto.name,
        description: dto.description,
      }),
    );
    // Keycloak returns 201 with no body; re-read by name to expose the id.
    return this.toResponse(await this.getRoleByName(dto.name));
  }

  async findAll(): Promise<RoleResponse[]> {
    // briefRepresentation=false so `attributes` (incl. our logical-delete flag)
    // come back — Keycloak's list endpoint omits them by default.
    const response = await this.admin.get<KeycloakRole[]>(
      "/roles?briefRepresentation=false",
    );
    return (response.body ?? [])
      .filter((role) => !this.isDeleted(role))
      .map((role) => this.toResponse(role));
  }

  async findOne(id: string): Promise<RoleResponse> {
    return this.toResponse(await this.getActiveRole(id));
  }

  async update(id: string, dto: UpdateRoleDto): Promise<RoleResponse> {
    const role = await this.getActiveRole(id);
    await this.writeRole(dto.name, () =>
      this.admin.put(this.roleByIdPath(id), {
        ...role,
        name: dto.name,
        description: dto.description,
      }),
    );
    return this.toResponse(await this.getRoleById(id));
  }

  async patch(id: string, dto: PatchRoleDto): Promise<RoleResponse> {
    const role = await this.getActiveRole(id);
    const name = dto.name ?? role.name;
    await this.writeRole(name, () =>
      this.admin.put(this.roleByIdPath(id), {
        ...role,
        name,
        description: dto.description ?? role.description,
      }),
    );
    return this.toResponse(await this.getRoleById(id));
  }

  async delete(id: string): Promise<void> {
    const role = await this.getActiveRole(id);
    await this.admin.put(this.roleByIdPath(id), {
      ...role,
      attributes: { ...(role.attributes ?? {}), [DELETED_ATTRIBUTE]: ["true"] },
    });

    // The flag only hides the role from this API; Keycloak still puts it in the
    // tokens of every user mapped to it. Strip those mappings so a deleted role
    // stops granting access.
    const ref = [{ id: role.id, name: role.name }];
    for (const userId of await this.directHolderIds(role.name)) {
      try {
        await this.admin.request("DELETE", this.userRealmMappingsPath(userId), ref);
      } catch (error) {
        // The user was removed meanwhile — nothing left to strip.
        if (!(error instanceof NotFoundError)) throw error;
      }
    }
  }

  async assignToUser(id: string, userId: string): Promise<void> {
    const role = await this.getActiveRole(id);
    await this.admin.post(this.userRealmMappingsPath(userId), [
      { id: role.id, name: role.name },
    ]);
  }

  async removeFromUser(id: string, userId: string): Promise<void> {
    // Deleted roles are accepted here so a mapping left behind by an
    // interrupted `remove` can still be cleaned up.
    const role = await this.getRoleById(id);
    // The Admin API expects the role refs in the body of the DELETE, which the
    // convenience `delete()` helper can't carry — go through `request()`.
    await this.admin.request("DELETE", this.userRealmMappingsPath(userId), [
      { id: role.id, name: role.name },
    ]);
  }

  /**
   * Runs a create/rename. The shared client phrases a 409 for users, so it is
   * restated for roles — and a name still held by a logically deleted role
   * (which Keycloak keeps) gets a message saying so, since the read paths of
   * this API no longer show that role.
   */
  private async writeRole(
    name: string,
    write: () => Promise<unknown>,
  ): Promise<void> {
    try {
      await write();
    } catch (error) {
      if (!(error instanceof ConflictError)) throw error;
      const holder = await this.getRoleByName(name).catch(() => undefined);
      throw new ConflictError(
        holder && this.isDeleted(holder)
          ? "Este nome pertence a um papel excluído; escolha outro nome."
          : "Já existe um papel com este nome.",
        "keycloak-admin",
        error.chain,
      );
    }
  }

  /**
   * `/roles-by-id` resolves client roles too (e.g. realm-management's
   * `manage-users`). This API only manages realm roles, so a client role is
   * reported as missing rather than exposed to reads and writes.
   */
  private async getRoleById(id: string): Promise<KeycloakRole> {
    const role = await this.readRole(this.roleByIdPath(id));
    if (role.clientRole) {
      throw new NotFoundError("Papel não encontrado no realm.", "keycloak-admin");
    }
    return role;
  }

  private async getRoleByName(name: string): Promise<KeycloakRole> {
    return this.readRole(`/roles/${encodeURIComponent(name)}`);
  }

  /**
   * The shared client raises a user-worded `NotFoundError` on an upstream 404;
   * restate it for roles so a missing role doesn't claim a missing user.
   */
  private async readRole(path: string): Promise<KeycloakRole> {
    try {
      const response = await this.admin.get<KeycloakRole>(path);
      return response.body;
    } catch (error) {
      if (error instanceof NotFoundError) {
        throw new NotFoundError("Papel não encontrado no realm.", "keycloak-admin");
      }
      throw error;
    }
  }

  /**
   * Fetches a role by id, treating both a missing role and a logically deleted
   * one as 404.
   */
  private async getActiveRole(id: string): Promise<KeycloakRole> {
    const role = await this.getRoleById(id);
    if (this.isDeleted(role)) {
      throw new NotFoundError(
        "Papel não encontrado no realm.",
        "keycloak-admin",
      );
    }
    return role;
  }

  /**
   * Ids of the users mapped directly to a realm role. Collected in full before
   * any mapping is removed — removing while paging would shift the offsets and
   * skip users.
   */
  private async directHolderIds(roleName: string): Promise<string[]> {
    const ids: string[] = [];
    for (let first = 0; ; first += HOLDERS_PAGE_SIZE) {
      const response = await this.admin.get<{ id: string }[]>(
        `/roles/${encodeURIComponent(roleName)}/users?first=${first}&max=${HOLDERS_PAGE_SIZE}`,
      );
      const page = response.body ?? [];
      ids.push(...page.map((user) => user.id));
      if (page.length < HOLDERS_PAGE_SIZE) return ids;
    }
  }

  private isDeleted(role: KeycloakRole): boolean {
    return role.attributes?.[DELETED_ATTRIBUTE]?.includes("true") ?? false;
  }

  private toResponse(role: KeycloakRole): RoleResponse {
    return { id: role.id, name: role.name, description: role.description };
  }

  private roleByIdPath(id: string): string {
    return `/roles-by-id/${encodeURIComponent(id)}`;
  }

  private userRealmMappingsPath(userId: string): string {
    return `/users/${encodeURIComponent(userId)}/role-mappings/realm`;
  }
}
