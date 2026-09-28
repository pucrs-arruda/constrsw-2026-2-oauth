import { RoleRepository } from '../../domain/repositories/role.repository';
import { RolesService } from './roles.service';

describe('RolesService', () => {
  let service: RolesService;
  let roles: jest.Mocked<RoleRepository>;

  beforeEach(() => {
    roles = {
      create: jest.fn(),
      findAll: jest.fn(),
      findById: jest.fn(),
      replace: jest.fn(),
      patch: jest.fn(),
      remove: jest.fn(),
      assignToUser: jest.fn(),
      removeFromUser: jest.fn(),
    };
    service = new RolesService(roles);
  });

  it('create delega ao repositorio e devolve o role criado', async () => {
    const role = { id: 'r1', name: 'coordenador', description: 'd' };
    roles.create.mockResolvedValue(role);

    await expect(service.create('tok', { name: 'coordenador', description: 'd' })).resolves.toBe(role);
    expect(roles.create).toHaveBeenCalledWith('tok', { name: 'coordenador', description: 'd' });
  });

  it('findAll e findOne (findById) delegam ao repositorio', async () => {
    await service.findAll('tok');
    await service.findOne('tok', 'r1');

    expect(roles.findAll).toHaveBeenCalledWith('tok');
    expect(roles.findById).toHaveBeenCalledWith('tok', 'r1');
  });

  it('replace (PUT) e patch (PATCH) usam operacoes distintas do repositorio', async () => {
    await service.replace('tok', 'r1', { name: 'novo' });
    await service.patch('tok', 'r1', { description: 'so desc' });

    expect(roles.replace).toHaveBeenCalledWith('tok', 'r1', { name: 'novo' });
    expect(roles.patch).toHaveBeenCalledWith('tok', 'r1', { description: 'so desc' });
  });

  it('remove, assignToUser e removeFromUser delegam ao repositorio', async () => {
    await service.remove('tok', 'r1');
    await service.assignToUser('tok', 'u1', 'r1');
    await service.removeFromUser('tok', 'u1', 'r1');

    expect(roles.remove).toHaveBeenCalledWith('tok', 'r1');
    expect(roles.assignToUser).toHaveBeenCalledWith('tok', 'u1', 'r1');
    expect(roles.removeFromUser).toHaveBeenCalledWith('tok', 'u1', 'r1');
  });
});
