import { KeycloakRoleRepository } from './keycloak-role.repository';
import { KeycloakClientService } from './keycloak-client.service';

describe('KeycloakRoleRepository', () => {
  let service: KeycloakRoleRepository;
  let keycloak: jest.Mocked<Pick<KeycloakClientService, 'adminRequest'>>;

  beforeEach(() => {
    keycloak = { adminRequest: jest.fn() };
    service = new KeycloakRoleRepository(keycloak as unknown as KeycloakClientService);
  });

  it('create cria o role por nome e depois busca o id gerado por GET /roles/{name}', async () => {
    keycloak.adminRequest
      .mockResolvedValueOnce({ data: undefined, headers: {} }) // POST /roles
      .mockResolvedValueOnce({
        data: { id: 'role-1', name: 'coordenador', description: 'Coordenador de curso' },
        headers: {},
      }); // GET /roles/coordenador

    const result = await service.create('token123', {
      name: 'coordenador',
      description: 'Coordenador de curso',
    });

    expect(keycloak.adminRequest).toHaveBeenNthCalledWith(1, 'POST', '/roles', 'token123', {
      data: { name: 'coordenador', description: 'Coordenador de curso' },
    });
    expect(keycloak.adminRequest).toHaveBeenNthCalledWith(
      2,
      'GET',
      '/roles/coordenador',
      'token123',
    );
    expect(result).toEqual({ id: 'role-1', name: 'coordenador', description: 'Coordenador de curso' });
  });

  it('findOne busca o role pelo endpoint roles-by-id (Admin API do Keycloak)', async () => {
    keycloak.adminRequest.mockResolvedValue({
      data: { id: 'role-1', name: 'coordenador' },
      headers: {},
    });

    const result = await service.findById('token123', 'role-1');

    expect(keycloak.adminRequest).toHaveBeenCalledWith('GET', '/roles-by-id/role-1', 'token123');
    expect(result).toEqual({ id: 'role-1', name: 'coordenador', description: undefined });
  });

  it('replace (PUT) busca o role atual e sobrescreve nome/descricao mantendo o resto do objeto', async () => {
    keycloak.adminRequest
      .mockResolvedValueOnce({
        data: { id: 'role-1', name: 'antigo', description: 'desc antiga', clientRole: false },
        headers: {},
      })
      .mockResolvedValueOnce({ data: undefined, headers: {} });

    await service.replace('token123', 'role-1', { name: 'novo', description: 'desc nova' });

    expect(keycloak.adminRequest).toHaveBeenNthCalledWith(2, 'PUT', '/roles-by-id/role-1', 'token123', {
      data: { id: 'role-1', name: 'novo', description: 'desc nova', clientRole: false },
    });
  });

  it('patch mantem os valores atuais dos campos que nao vierem no body', async () => {
    keycloak.adminRequest
      .mockResolvedValueOnce({
        data: { id: 'role-1', name: 'atual', description: 'descricao atual' },
        headers: {},
      })
      .mockResolvedValueOnce({ data: undefined, headers: {} });

    await service.patch('token123', 'role-1', { description: 'nova descricao' });

    expect(keycloak.adminRequest).toHaveBeenNthCalledWith(2, 'PUT', '/roles-by-id/role-1', 'token123', {
      data: { id: 'role-1', name: 'atual', description: 'nova descricao' },
    });
  });

  it('remove exclui de fato o role - nao ha exclusao logica para roles no Keycloak', async () => {
    keycloak.adminRequest.mockResolvedValue({ data: undefined, headers: {} });

    await service.remove('token123', 'role-1');

    expect(keycloak.adminRequest).toHaveBeenCalledWith('DELETE', '/roles-by-id/role-1', 'token123');
  });

  it('assignToUser busca o role e atribui via POST .../role-mappings/realm', async () => {
    keycloak.adminRequest
      .mockResolvedValueOnce({ data: { id: 'role-1', name: 'coordenador' }, headers: {} })
      .mockResolvedValueOnce({ data: undefined, headers: {} });

    await service.assignToUser('token123', 'user-1', 'role-1');

    expect(keycloak.adminRequest).toHaveBeenNthCalledWith(
      2,
      'POST',
      '/users/user-1/role-mappings/realm',
      'token123',
      { data: [{ id: 'role-1', name: 'coordenador' }] },
    );
  });

  it('removeFromUser busca o role e remove via DELETE .../role-mappings/realm', async () => {
    keycloak.adminRequest
      .mockResolvedValueOnce({ data: { id: 'role-1', name: 'coordenador' }, headers: {} })
      .mockResolvedValueOnce({ data: undefined, headers: {} });

    await service.removeFromUser('token123', 'user-1', 'role-1');

    expect(keycloak.adminRequest).toHaveBeenNthCalledWith(
      2,
      'DELETE',
      '/users/user-1/role-mappings/realm',
      'token123',
      { data: [{ id: 'role-1', name: 'coordenador' }] },
    );
  });
});
