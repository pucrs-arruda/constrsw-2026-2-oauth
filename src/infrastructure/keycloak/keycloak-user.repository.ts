import { Injectable } from '@nestjs/common';
import {
  NewUser,
  User,
  UserChanges,
  UserFilter,
} from '../../domain/entities/user.entity';
import { UserRepository } from '../../domain/repositories/user.repository';
import { KeycloakClientService } from './keycloak-client.service';
import {
  KeycloakUserRepresentation,
  toKeycloakNewUser,
  toKeycloakUserChanges,
  toUser,
} from './mappers/user.mapper';

@Injectable()
export class KeycloakUserRepository implements UserRepository {
  constructor(private readonly keycloak: KeycloakClientService) {}

  async create(token: string, data: NewUser): Promise<User> {
    const { headers } = await this.keycloak.adminRequest(
      'POST',
      '/users',
      token,
      { data: toKeycloakNewUser(data) },
    );

    const location = headers['location'] ?? headers['Location'];
    const id = location ? location.split('/').pop()! : '';

    return {
      id,
      username: data.username,
      firstName: data.firstName,
      lastName: data.lastName,
      enabled: true,
    };
  }

  async findAll(token: string, filter: UserFilter): Promise<User[]> {
    const { data } = await this.keycloak.adminRequest<
      KeycloakUserRepresentation[]
    >('GET', '/users', token, {
      params: filter.enabled === undefined ? {} : { enabled: filter.enabled },
    });
    return data.map(toUser);
  }

  async findById(token: string, id: string): Promise<User> {
    const { data } = await this.keycloak.adminRequest<KeycloakUserRepresentation>(
      'GET',
      `/users/${id}`,
      token,
    );
    return toUser(data);
  }

  async update(token: string, id: string, changes: UserChanges): Promise<void> {
    await this.keycloak.adminRequest('PUT', `/users/${id}`, token, {
      data: toKeycloakUserChanges(changes),
    });
  }

  async updatePassword(
    token: string,
    id: string,
    password: string,
  ): Promise<void> {
    await this.keycloak.adminRequest('PUT', `/users/${id}/reset-password`, token, {
      data: { type: 'password', value: password, temporary: false },
    });
  }

  async disable(token: string, id: string): Promise<void> {
    await this.keycloak.adminRequest('PUT', `/users/${id}`, token, {
      data: { enabled: false },
    });
  }
}
