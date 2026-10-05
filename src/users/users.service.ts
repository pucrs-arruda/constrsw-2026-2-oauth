import { Injectable, NotFoundException } from "@nestjs/common";
import { KeycloakClient, KeycloakUser } from "../auth/keycloak.client";
import {
  KeycloakDependencyError,
  KeycloakError,
  ValidationError,
} from "../common/errors";
import { KeycloakAdminClient } from "../keycloak/keycloak-admin.client";
import { CreateUserDto } from "./dto/create-user.dto";
import { PatchUserDto } from "./dto/patch-user.dto";
import { ReplaceUserDto } from "./dto/replace-user.dto";
import {
  CreatedUser,
  KeycloakUserRepresentation,
} from "./interfaces/user.interface";

export interface UserResponse {
  id: string;
  username: string;
  "first-name": string;
  "last-name": string;
  enabled: boolean;
}

@Injectable()
export class UsersService {
  constructor(
    private readonly keycloak: KeycloakClient,
    private readonly admin: KeycloakAdminClient,
  ) {}

  async list(accessToken: string, enabled?: boolean): Promise<UserResponse[]> {
    const users = await this.keycloak.listUsers(accessToken, enabled);
    return users
      .filter((user) => !user.username.startsWith("service-account-"))
      .map((user) => this.toResponse(user));
  }

  async get(accessToken: string, id: string): Promise<UserResponse> {
    const user = await this.find(accessToken, id);
    return this.toResponse(user);
  }

  async create(
    accessToken: string,
    input: CreateUserDto,
  ): Promise<CreatedUser> {
    const representation: KeycloakUserRepresentation = {
      username: input.username,
      email: input.username,
      firstName: input["first-name"],
      lastName: input["last-name"],
      // Novo usuário nasce habilitado; `enabled` não é entrada do request.
      enabled: true,
      emailVerified: false,
      credentials: [
        { type: "password", value: input.password, temporary: false },
      ],
    };

    const response = await this.admin.post(
      "/users",
      representation,
      accessToken,
    );
    const id = this.idFromLocation(response.headers.get("location"));
    return this.toCreatedResponse(
      await this.admin.get(this.userPath(id), accessToken),
    );
  }

  async update(
    accessToken: string,
    id: string,
    input: ReplaceUserDto,
  ): Promise<void> {
    await this.admin.put(
      this.userPath(id),
      {
        username: input.username,
        email: input.username,
        firstName: input["first-name"],
        lastName: input["last-name"],
        // `enabled` é omitido de propósito: o Admin API preserva o estado
        // atual, então um PUT de rotina não reativa um usuário desabilitado.
        emailVerified: false,
      },
      accessToken,
    );
  }

  async patch(
    accessToken: string,
    id: string,
    input: PatchUserDto,
  ): Promise<void> {
    const { password, username } = input;
    // A API pública usa `first-name`/`last-name`; o Keycloak espera
    // `firstName`/`lastName`.
    const fields: KeycloakUserRepresentation = {};
    if (username !== undefined) fields.username = username;
    if (input["first-name"] !== undefined) fields.firstName = input["first-name"];
    if (input["last-name"] !== undefined) fields.lastName = input["last-name"];

    if (password === undefined && Object.keys(fields).length === 0) {
      throw new ValidationError(
        "Informe ao menos um campo para alterar.",
        "users",
      );
    }

    if (password !== undefined) {
      await this.admin.put(
        `${this.userPath(id)}/reset-password`,
        {
          type: "password",
          value: password,
          temporary: false,
        },
        accessToken,
      );
    }
    if (Object.keys(fields).length > 0) {
      await this.admin.put(this.userPath(id), fields, accessToken);
    }
  }

  async delete(accessToken: string, id: string): Promise<void> {
    await this.admin.put(this.userPath(id), { enabled: false }, accessToken);
  }

  private async find(accessToken: string, id: string): Promise<KeycloakUser> {
    try {
      return await this.keycloak.getUser(accessToken, id);
    } catch (error) {
      if (
        error instanceof KeycloakDependencyError &&
        error.upstreamStatus === 404
      ) {
        throw new NotFoundException();
      }
      throw error;
    }
  }

  private toResponse(user: KeycloakUser): UserResponse {
    return {
      id: user.id,
      username: user.username,
      "first-name": user.firstName ?? "",
      "last-name": user.lastName ?? "",
      enabled: user.enabled,
    };
  }

  private toCreatedResponse(response: { body: unknown }): CreatedUser {
    const user = response.body as {
      id?: string;
      username?: string;
      firstName?: string;
      lastName?: string;
      enabled?: boolean;
    };
    return {
      id: user.id ?? "",
      username: user.username ?? "",
      "first-name": user.firstName ?? "",
      "last-name": user.lastName ?? "",
      enabled: user.enabled ?? false,
    };
  }

  private userPath(id: string): string {
    return `/users/${encodeURIComponent(id)}`;
  }

  private idFromLocation(location: string | null): string {
    const id = location?.split("/").filter(Boolean).pop();
    if (!id || id === "users") {
      throw new KeycloakError(
        "O usuário pode ter sido criado, mas o provedor de identidade não devolveu o identificador.",
        { source: "keycloak-admin" },
      );
    }
    return id;
  }
}
