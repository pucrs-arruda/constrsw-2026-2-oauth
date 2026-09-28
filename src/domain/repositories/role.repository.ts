import { NewRole, Role, RoleChanges } from '../entities/role.entity';

export const ROLE_REPOSITORY = Symbol('ROLE_REPOSITORY');

export interface RoleRepository {
  create(token: string, data: NewRole): Promise<Role>;
  findAll(token: string): Promise<Role[]>;
  findById(token: string, id: string): Promise<Role>;
  /** PUT: sobrescreve name e description. */
  replace(token: string, id: string, data: NewRole): Promise<void>;
  /** PATCH: preserva os campos nao informados. */
  patch(token: string, id: string, changes: RoleChanges): Promise<void>;
  remove(token: string, id: string): Promise<void>;
  assignToUser(token: string, userId: string, roleId: string): Promise<void>;
  removeFromUser(token: string, userId: string, roleId: string): Promise<void>;
}
