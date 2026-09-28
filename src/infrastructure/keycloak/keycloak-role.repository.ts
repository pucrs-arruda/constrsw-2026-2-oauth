import { Injectable } from '@nestjs/common';
import { NewRole, Role, RoleChanges } from '../../domain/entities/role.entity';
import { RoleRepository } from '../../domain/repositories/role.repository';
import { KeycloakClientService } from './keycloak-client.service';
import { KeycloakRoleRepresentation, toRole } from './mappers/role.mapper';

@Injectable()
export class KeycloakRoleRepository implements RoleRepository {
  constructor(private readonly keycloak: KeycloakClientService) {}

  async create(token: string, data: NewRole): Promise<Role> {
    await this.keycloak.adminRequest('POST', '/roles', token, {
      data: { name: data.name, description: data.description },
    });
    // Keycloak cria roles por nome; buscamos o id gerado em seguida.
    const { data: created } =
      await this.keycloak.adminRequest<KeycloakRoleRepresentation>(
        'GET',
        `/roles/${encodeURIComponent(data.name)}`,
        token,
      );
    return toRole(created);
  }

  async findAll(token: string): Promise<Role[]> {
    const { data } = await this.keycloak.adminRequest<
      KeycloakRoleRepresentation[]
    >('GET', '/roles', token);
    return data.map(toRole);
  }

  async findById(token: string, id: string): Promise<Role> {
    return toRole(await this.getRaw(token, id));
  }

  async replace(token: string, id: string, data: NewRole): Promise<void> {
    const current = await this.getRaw(token, id);
    await this.keycloak.adminRequest('PUT', `/roles-by-id/${id}`, token, {
      data: { ...current, name: data.name, description: data.description },
    });
  }

  async patch(token: string, id: string, changes: RoleChanges): Promise<void> {
    const current = await this.getRaw(token, id);
    await this.keycloak.adminRequest('PUT', `/roles-by-id/${id}`, token, {
      data: {
        ...current,
        name: changes.name ?? current.name,
        description: changes.description ?? current.description,
      },
    });
  }

  /**
   * O Keycloak nao possui flag de habilitado/desabilitado para roles
   * (diferente de usuarios); aqui a exclusao remove de fato o role do realm -
   * decisao documentada no README.md.
   */
  async remove(token: string, id: string): Promise<void> {
    await this.keycloak.adminRequest('DELETE', `/roles-by-id/${id}`, token);
  }

  async assignToUser(
    token: string,
    userId: string,
    roleId: string,
  ): Promise<void> {
    const role = await this.getRaw(token, roleId);
    await this.keycloak.adminRequest(
      'POST',
      `/users/${userId}/role-mappings/realm`,
      token,
      { data: [{ id: role.id, name: role.name }] },
    );
  }

  async removeFromUser(
    token: string,
    userId: string,
    roleId: string,
  ): Promise<void> {
    const role = await this.getRaw(token, roleId);
    await this.keycloak.adminRequest(
      'DELETE',
      `/users/${userId}/role-mappings/realm`,
      token,
      { data: [{ id: role.id, name: role.name }] },
    );
  }

  private async getRaw(
    token: string,
    id: string,
  ): Promise<KeycloakRoleRepresentation> {
    const { data } = await this.keycloak.adminRequest<KeycloakRoleRepresentation>(
      'GET',
      `/roles-by-id/${id}`,
      token,
    );
    return data;
  }
}
