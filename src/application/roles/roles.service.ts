import { Inject, Injectable } from '@nestjs/common';
import { NewRole, Role, RoleChanges } from '../../domain/entities/role.entity';
import {
  ROLE_REPOSITORY,
  RoleRepository,
} from '../../domain/repositories/role.repository';

@Injectable()
export class RolesService {
  constructor(
    @Inject(ROLE_REPOSITORY) private readonly roles: RoleRepository,
  ) {}

  create(token: string, data: NewRole): Promise<Role> {
    return this.roles.create(token, data);
  }

  findAll(token: string): Promise<Role[]> {
    return this.roles.findAll(token);
  }

  findOne(token: string, id: string): Promise<Role> {
    return this.roles.findById(token, id);
  }

  replace(token: string, id: string, data: NewRole): Promise<void> {
    return this.roles.replace(token, id, data);
  }

  patch(token: string, id: string, changes: RoleChanges): Promise<void> {
    return this.roles.patch(token, id, changes);
  }

  /** o provedor nao tem "desabilitar" role: a exclusao remove de fato (ver README). */
  remove(token: string, id: string): Promise<void> {
    return this.roles.remove(token, id);
  }

  assignToUser(token: string, userId: string, roleId: string): Promise<void> {
    return this.roles.assignToUser(token, userId, roleId);
  }

  removeFromUser(token: string, userId: string, roleId: string): Promise<void> {
    return this.roles.removeFromUser(token, userId, roleId);
  }
}
