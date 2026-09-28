import { toRepresentation, UPDATABLE_USER_FIELDS } from './user.types';

describe('toRepresentation', () => {
  it('maps Keycloak field names to the hyphenated wire shape', () => {
    expect(
      toRepresentation({
        id: 'user-id',
        username: 'aluno@pucrs.br',
        firstName: 'Ana',
        lastName: 'Silva',
        enabled: true,
      }),
    ).toEqual({
      id: 'user-id',
      username: 'aluno@pucrs.br',
      'first-name': 'Ana',
      'last-name': 'Silva',
      enabled: true,
    });
  });

  it('defaults missing id, username and enabled rather than leaking undefined', () => {
    expect(toRepresentation({})).toEqual({
      id: '',
      username: '',
      'first-name': undefined,
      'last-name': undefined,
      enabled: false,
    });
  });
});

describe('UPDATABLE_USER_FIELDS', () => {
  it('excludes username, which is the account identity', () => {
    expect(UPDATABLE_USER_FIELDS).toEqual(['first-name', 'last-name', 'enabled']);
    expect(UPDATABLE_USER_FIELDS).not.toContain('username');
  });
});
