import { HttpStatus } from '@nestjs/common';

import {
  roleBadRequest,
  roleConflict,
  roleNotFound,
  roleUpstreamFailure,
} from './roles.exceptions';

describe('roles.exceptions', () => {
  it('roleBadRequest maps to OA-400', () => {
    const error = roleBadRequest('bad body');
    expect(error.getStatus()).toBe(HttpStatus.BAD_REQUEST);
    expect(error.toEnvelope()).toMatchObject({
      error_code: 'OA-400',
      error_description: 'bad body',
    });
  });

  it('roleNotFound maps to OA-404', () => {
    const error = roleNotFound('missing');
    expect(error.getStatus()).toBe(HttpStatus.NOT_FOUND);
    expect(error.toEnvelope().error_code).toBe('OA-404');
  });

  it('roleConflict maps to OA-409', () => {
    const error = roleConflict('dup');
    expect(error.getStatus()).toBe(HttpStatus.CONFLICT);
    expect(error.toEnvelope().error_code).toBe('OA-409');
  });

  it('roleUpstreamFailure relays an arbitrary status', () => {
    const error = roleUpstreamFailure(HttpStatus.BAD_GATEWAY, 'upstream broke');
    expect(error.getStatus()).toBe(HttpStatus.BAD_GATEWAY);
    expect(error.toEnvelope().error_description).toBe('upstream broke');
  });
});
