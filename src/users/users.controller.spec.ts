import { OaException } from '../errors';
import { KeycloakUsersService } from './keycloak-users.service';
import { UsersController } from './users.controller';

const USER_ID = '11111111-1111-4111-8111-111111111111';

async function rejection(promise: Promise<unknown>): Promise<OaException> {
  try {
    await promise;
  } catch (error) {
    return error as OaException;
  }
  throw new Error('expected the call to reject');
}

describe('UsersController', () => {
  let controller: UsersController;
  let users: jest.Mocked<
    Pick<
      KeycloakUsersService,
      'createUser' | 'listUsers' | 'getUser' | 'updateUser' | 'changePassword' | 'disableUser'
    >
  >;

  const stored = {
    id: USER_ID,
    username: 'aluno@pucrs.br',
    firstName: 'Ana',
    lastName: 'Silva',
    enabled: true,
  };

  beforeEach(() => {
    users = {
      createUser: jest.fn().mockResolvedValue(stored),
      listUsers: jest.fn().mockResolvedValue([stored]),
      getUser: jest.fn().mockResolvedValue(stored),
      updateUser: jest.fn().mockResolvedValue(undefined),
      changePassword: jest.fn().mockResolvedValue(undefined),
      disableUser: jest.fn().mockResolvedValue(undefined),
    };
    controller = new UsersController(users as unknown as KeycloakUsersService);
  });

  describe('POST /users', () => {
    const valid = {
      username: 'aluno@pucrs.br',
      password: 'segredo',
      'first-name': 'Ana',
      'last-name': 'Silva',
    };

    it('answers with the hyphenated representation from the brief', async () => {
      const created = await controller.create(valid);

      expect(created).toEqual({
        id: USER_ID,
        username: 'aluno@pucrs.br',
        'first-name': 'Ana',
        'last-name': 'Silva',
        enabled: true,
      });
    });

    it('stores the username as the e-mail too, since the realm uses e-mail as username', async () => {
      await controller.create(valid);

      expect(users.createUser).toHaveBeenCalledWith(
        expect.objectContaining({
          username: 'aluno@pucrs.br',
          email: 'aluno@pucrs.br',
          firstName: 'Ana',
          lastName: 'Silva',
        }),
        'segredo',
      );
    });

    it.each([
      ['no at sign', 'alunopucrs.br'],
      ['no domain', 'aluno@'],
      ['no local part', '@pucrs.br'],
      ['domain without a dot', 'aluno@pucrs'],
      ['a space inside', 'al uno@pucrs.br'],
      ['two at signs', 'aluno@@pucrs.br'],
      ['a trailing dot in the local part', 'aluno.@pucrs.br'],
    ])('rejects a username with %s', async (_label, username) => {
      const error = await rejection(controller.create({ ...valid, username }));

      expect(error.getStatus()).toBe(400);
      expect(error.toEnvelope().error_code).toBe('OA-400');
      expect(users.createUser).not.toHaveBeenCalled();
    });

    it.each([
      'aluno@pucrs.br',
      'ana.silva@edu.pucrs.br',
      "o'brien+tag@pucrs.br",
    ])('accepts the valid address %p', async (username) => {
      await expect(controller.create({ ...valid, username })).resolves.toBeDefined();
    });

    it.each(['username', 'password'])('requires %s', async (field) => {
      const body = { ...valid, [field]: undefined };

      const error = await rejection(controller.create(body));

      expect(error.getStatus()).toBe(400);
      expect(error.toEnvelope().error_description).toContain(field);
    });

    it('rejects a blank password rather than creating an unusable account', async () => {
      const error = await rejection(controller.create({ ...valid, password: '   ' }));

      expect(error.getStatus()).toBe(400);
      expect(users.createUser).not.toHaveBeenCalled();
    });

    it('rejects a non-string first-name', async () => {
      const error = await rejection(
        controller.create({ ...valid, 'first-name': 42 } as never),
      );

      expect(error.getStatus()).toBe(400);
      expect(users.createUser).not.toHaveBeenCalled();
    });
  });

  describe('GET /users', () => {
    it('returns only enabled users when no query string is given', async () => {
      await controller.list(undefined);

      // The brief describes the response as the registered *and enabled* users.
      expect(users.listUsers).toHaveBeenCalledWith(true);
    });

    it.each([
      ['true', true],
      ['false', false],
    ])('honours ?enabled=%s', async (value, expected) => {
      await controller.list(value);

      expect(users.listUsers).toHaveBeenCalledWith(expected);
    });

    it.each(['yes', '1', 'TRUE', ''])('rejects ?enabled=%p', async (value) => {
      const error = await rejection(controller.list(value));

      expect(error.getStatus()).toBe(400);
      expect(users.listUsers).not.toHaveBeenCalled();
    });
  });

  describe('GET /users/:id', () => {
    it('returns the representation', async () => {
      await expect(controller.get(USER_ID)).resolves.toMatchObject({
        id: USER_ID,
        'first-name': 'Ana',
      });
    });

    it('rejects an id that is not a UUID before calling Keycloak', async () => {
      const error = await rejection(controller.get('not-a-uuid'));

      expect(error.getStatus()).toBe(400);
      expect(users.getUser).not.toHaveBeenCalled();
    });
  });

  describe('PUT /users/:id', () => {
    it('forwards the allowed attributes', async () => {
      await controller.update(USER_ID, {
        'first-name': 'Beatriz',
        'last-name': 'Souza',
        enabled: false,
      });

      expect(users.updateUser).toHaveBeenCalledWith(USER_ID, {
        firstName: 'Beatriz',
        lastName: 'Souza',
        enabled: false,
      });
    });

    it('refuses to change the username, which is the account identity', async () => {
      const error = await rejection(
        controller.update(USER_ID, { username: 'outro@pucrs.br' } as never),
      );

      expect(error.getStatus()).toBe(400);
      expect(error.toEnvelope().error_description).toContain('username');
      expect(users.updateUser).not.toHaveBeenCalled();
    });

    it('refuses any other unsupported field', async () => {
      const error = await rejection(
        controller.update(USER_ID, { password: 'x' } as never),
      );

      expect(error.getStatus()).toBe(400);
      expect(users.updateUser).not.toHaveBeenCalled();
    });

    it('refuses an empty body rather than issuing a no-op write', async () => {
      const error = await rejection(controller.update(USER_ID, {}));

      expect(error.getStatus()).toBe(400);
      expect(users.updateUser).not.toHaveBeenCalled();
    });

    it('refuses a non-boolean enabled', async () => {
      const error = await rejection(
        controller.update(USER_ID, { enabled: 'false' } as never),
      );

      expect(error.getStatus()).toBe(400);
    });

    it('refuses a non-object body', async () => {
      const error = await rejection(controller.update(USER_ID, null as never));

      expect(error.getStatus()).toBe(400);
      expect(users.updateUser).not.toHaveBeenCalled();
    });
  });

  describe('PATCH /users/:id', () => {
    it('changes the password', async () => {
      await controller.changePassword(USER_ID, { password: 'nova-senha' });

      expect(users.changePassword).toHaveBeenCalledWith(USER_ID, 'nova-senha');
    });

    it('requires a password', async () => {
      const error = await rejection(controller.changePassword(USER_ID, {}));

      expect(error.getStatus()).toBe(400);
      expect(users.changePassword).not.toHaveBeenCalled();
    });
  });

  describe('DELETE /users/:id', () => {
    it('disables the user instead of removing it', async () => {
      await controller.remove(USER_ID);

      expect(users.disableUser).toHaveBeenCalledWith(USER_ID);
    });

    it('rejects a malformed id', async () => {
      const error = await rejection(controller.remove('123'));

      expect(error.getStatus()).toBe(400);
      expect(users.disableUser).not.toHaveBeenCalled();
    });
  });
});
