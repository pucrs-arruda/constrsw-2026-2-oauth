import { KeycloakUserRepository } from './keycloak-user.repository';
import { KeycloakClientService } from './keycloak-client.service';

describe('KeycloakUserRepository', () => {
  let service: KeycloakUserRepository;
  let keycloak: jest.Mocked<Pick<KeycloakClientService, 'adminRequest'>>;

  beforeEach(() => {
    keycloak = { adminRequest: jest.fn() };
    service = new KeycloakUserRepository(keycloak as unknown as KeycloakClientService);
  });

  describe('create', () => {
    it('monta o payload do Keycloak (username=email, credentials) e extrai o id do header Location', async () => {
      keycloak.adminRequest.mockResolvedValue({
        data: undefined,
        headers: { location: 'http://keycloak/admin/realms/constrsw/users/abc-123' },
      });

      const result = await service.create('token123', {
        username: 'joao@pucrs.br',
        password: 'trocarDepois123',
        firstName: 'Joao',
        lastName: 'Silva',
      });

      expect(keycloak.adminRequest).toHaveBeenCalledWith('POST', '/users', 'token123', {
        data: {
          username: 'joao@pucrs.br',
          email: 'joao@pucrs.br',
          enabled: true,
          firstName: 'Joao',
          lastName: 'Silva',
          credentials: [{ type: 'password', value: 'trocarDepois123', temporary: false }],
        },
      });
      expect(result).toEqual({
        id: 'abc-123',
        username: 'joao@pucrs.br',
        firstName: 'Joao',
        lastName: 'Silva',
        enabled: true,
      });
    });

    it('funciona tambem com o header "Location" capitalizado', async () => {
      keycloak.adminRequest.mockResolvedValue({
        data: undefined,
        headers: { Location: 'http://keycloak/admin/realms/constrsw/users/xyz-789' },
      });

      const result = await service.create('token123', {
        username: 'ana@pucrs.br',
        password: 'x',
        firstName: 'Ana',
        lastName: 'Souza',
      });

      expect(result.id).toBe('xyz-789');
    });
  });

  describe('findAll', () => {
    it('nao envia o parametro enabled quando o filtro nao e informado', async () => {
      keycloak.adminRequest.mockResolvedValue({ data: [], headers: {} });

      await service.findAll('token123', {});

      expect(keycloak.adminRequest).toHaveBeenCalledWith('GET', '/users', 'token123', {
        params: {},
      });
    });

    it('repassa o filtro enabled quando informado', async () => {
      keycloak.adminRequest.mockResolvedValue({ data: [], headers: {} });

      await service.findAll('token123', { enabled: true });

      expect(keycloak.adminRequest).toHaveBeenCalledWith('GET', '/users', 'token123', {
        params: { enabled: true },
      });
    });

    it('mapeia a representacao do Keycloak (firstName/lastName) para a entidade User do dominio', async () => {
      keycloak.adminRequest.mockResolvedValue({
        data: [{ id: '1', username: 'a@pucrs.br', firstName: 'A', lastName: 'B', enabled: true }],
        headers: {},
      });

      const result = await service.findAll('token123', {});

      expect(result).toEqual([
        { id: '1', username: 'a@pucrs.br', firstName: 'A', lastName: 'B', enabled: true },
      ]);
    });
  });

  describe('update (PUT)', () => {
    it('envia apenas os campos informados (atualizacao parcial)', async () => {
      keycloak.adminRequest.mockResolvedValue({ data: undefined, headers: {} });

      await service.update('token123', 'user-1', { firstName: 'Novo Nome' });

      expect(keycloak.adminRequest).toHaveBeenCalledWith('PUT', '/users/user-1', 'token123', {
        data: { firstName: 'Novo Nome' },
      });
    });

    it('atualiza tambem o email quando o username muda', async () => {
      keycloak.adminRequest.mockResolvedValue({ data: undefined, headers: {} });

      await service.update('token123', 'user-1', { username: 'novo@pucrs.br' });

      expect(keycloak.adminRequest).toHaveBeenCalledWith('PUT', '/users/user-1', 'token123', {
        data: { username: 'novo@pucrs.br', email: 'novo@pucrs.br' },
      });
    });
  });

  it('updatePassword (PATCH) chama reset-password com a nova senha', async () => {
    keycloak.adminRequest.mockResolvedValue({ data: undefined, headers: {} });

    await service.updatePassword('token123', 'user-1', 'novaSenha456');

    expect(keycloak.adminRequest).toHaveBeenCalledWith(
      'PUT',
      '/users/user-1/reset-password',
      'token123',
      { data: { type: 'password', value: 'novaSenha456', temporary: false } },
    );
  });

  it('disable (DELETE) faz exclusao logica (enabled=false), sem remover o usuario do Keycloak', async () => {
    keycloak.adminRequest.mockResolvedValue({ data: undefined, headers: {} });

    await service.disable('token123', 'user-1');

    expect(keycloak.adminRequest).toHaveBeenCalledWith('PUT', '/users/user-1', 'token123', {
      data: { enabled: false },
    });
  });
});
