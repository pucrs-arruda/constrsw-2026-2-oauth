import { NewUser, User, UserChanges, UserFilter } from '../entities/user.entity';

export const USER_REPOSITORY = Symbol('USER_REPOSITORY');

/**
 * Todas as operacoes recebem o bearer token do chamador, repassado ao
 * provedor de identidade (SPEC.md 1.1: sem token de servico).
 */
export interface UserRepository {
  create(token: string, data: NewUser): Promise<User>;
  findAll(token: string, filter: UserFilter): Promise<User[]>;
  findById(token: string, id: string): Promise<User>;
  update(token: string, id: string, changes: UserChanges): Promise<void>;
  updatePassword(token: string, id: string, password: string): Promise<void>;
  /** exclusao logica */
  disable(token: string, id: string): Promise<void>;
}
