import { CANONICAL_ROLE_NAMES } from './role.types';

describe('CANONICAL_ROLE_NAMES', () => {
  it('lists the four roles the realm defines', () => {
    expect(CANONICAL_ROLE_NAMES).toEqual([
      'administrator',
      'coordinator',
      'professor',
      'student',
    ]);
  });
});
