import { Inject, Injectable } from '@nestjs/common';
import {
  NewUser,
  User,
  UserChanges,
  UserFilter,
} from '../../domain/entities/user.entity';
import {
  USER_REPOSITORY,
  UserRepository,
} from '../../domain/repositories/user.repository';

@Injectable()
export class UsersService {
  constructor(
    @Inject(USER_REPOSITORY) private readonly users: UserRepository,
  ) {}

  create(token: string, data: NewUser): Promise<User> {
    return this.users.create(token, data);
  }

  findAll(token: string, filter: UserFilter): Promise<User[]> {
    return this.users.findAll(token, filter);
  }

  findOne(token: string, id: string): Promise<User> {
    return this.users.findById(token, id);
  }

  update(token: string, id: string, changes: UserChanges): Promise<void> {
    return this.users.update(token, id, changes);
  }

  updatePassword(token: string, id: string, password: string): Promise<void> {
    return this.users.updatePassword(token, id, password);
  }

  /** exclusao logica: desabilita o usuario (nao remove do provedor) */
  disable(token: string, id: string): Promise<void> {
    return this.users.disable(token, id);
  }
}
