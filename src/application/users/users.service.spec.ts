import { UserRepository } from '../../domain/repositories/user.repository';
import { UsersService } from './users.service';

describe('UsersService', () => {
  let service: UsersService;
  let users: jest.Mocked<UserRepository>;

  beforeEach(() => {
    users = {
      create: jest.fn(),
      findAll: jest.fn(),
      findById: jest.fn(),
      update: jest.fn(),
      updatePassword: jest.fn(),
      disable: jest.fn(),
    };
    service = new UsersService(users);
  });

  it('create delega ao repositorio com o token do chamador e devolve o usuario criado', async () => {
    const user = { id: '1', username: 'a@pucrs.br', firstName: 'A', lastName: 'B', enabled: true };
    users.create.mockResolvedValue(user);
    const data = { username: 'a@pucrs.br', password: 'x', firstName: 'A', lastName: 'B' };

    await expect(service.create('tok', data)).resolves.toBe(user);
    expect(users.create).toHaveBeenCalledWith('tok', data);
  });

  it('findAll repassa o filtro', async () => {
    users.findAll.mockResolvedValue([]);

    await service.findAll('tok', { enabled: true });

    expect(users.findAll).toHaveBeenCalledWith('tok', { enabled: true });
  });

  it('findOne usa findById', async () => {
    await service.findOne('tok', 'u1');

    expect(users.findById).toHaveBeenCalledWith('tok', 'u1');
  });

  it('update, updatePassword e disable delegam ao repositorio', async () => {
    await service.update('tok', 'u1', { firstName: 'N' });
    await service.updatePassword('tok', 'u1', 'nova');
    await service.disable('tok', 'u1');

    expect(users.update).toHaveBeenCalledWith('tok', 'u1', { firstName: 'N' });
    expect(users.updatePassword).toHaveBeenCalledWith('tok', 'u1', 'nova');
    expect(users.disable).toHaveBeenCalledWith('tok', 'u1');
  });

  it('propaga erros de dominio do repositorio sem tratar', async () => {
    const error = new Error('falha');
    users.findById.mockRejectedValue(error);

    await expect(service.findOne('tok', 'u1')).rejects.toBe(error);
  });
});
