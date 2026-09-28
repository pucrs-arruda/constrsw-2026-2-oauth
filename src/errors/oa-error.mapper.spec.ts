import { BadRequestException, HttpException, HttpStatus } from '@nestjs/common';

import { OA_ERROR_CODE, OA_ERROR_SOURCE } from './oa-error';
import { OaErrorMapper, OaException } from './oa-error.mapper';

describe('OaErrorMapper', () => {
  describe('local failures', () => {
    it('maps a local bad-request into OA-400 with an error_stack array of objects', () => {
      const exception = OaErrorMapper.badRequest('username is required');
      const { status, body } = OaErrorMapper.toResponse(exception);

      expect(status).toBe(HttpStatus.BAD_REQUEST);
      expect(body.error_code).toBe(OA_ERROR_CODE.BAD_REQUEST);
      expect(body.error_source).toBe(OA_ERROR_SOURCE);
      expect(body.error_description).toBe('username is required');
      expect(Array.isArray(body.error_stack)).toBe(true);
      expect(body.error_stack.every((entry) => typeof entry === 'object')).toBe(true);
    });

    it('carries a caller-supplied cause into the stack', () => {
      const exception = OaErrorMapper.notFound('user not found', { userId: 'abc' });
      const { body } = OaErrorMapper.toResponse(exception);

      expect(body.error_code).toBe(OA_ERROR_CODE.NOT_FOUND);
      expect(body.error_stack).toEqual([{ userId: 'abc' }]);
    });

    it('maps forbidden() to OA-403', () => {
      const exception = OaErrorMapper.forbidden('nope');
      expect(exception.getStatus()).toBe(HttpStatus.FORBIDDEN);
      expect(exception.toEnvelope().error_code).toBe(OA_ERROR_CODE.FORBIDDEN);
    });

    it('maps unauthorized() to OA-401', () => {
      const exception = OaErrorMapper.unauthorized('no token');
      expect(exception.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
      expect(exception.toEnvelope().error_code).toBe(OA_ERROR_CODE.UNAUTHORIZED);
    });

    it('fromStatus falls back to OA-500 for the internal server error status itself', () => {
      const exception = OaErrorMapper.fromStatus(
        HttpStatus.INTERNAL_SERVER_ERROR,
        'boom',
      );
      expect(exception.toEnvelope().error_code).toBe(OA_ERROR_CODE.INTERNAL);
    });

    it('fromStatus keeps the numeric status for an unmapped gateway error', () => {
      const exception = OaErrorMapper.fromStatus(HttpStatus.BAD_GATEWAY, 'upstream down');
      expect(exception.toEnvelope().error_code).toBe('OA-502');
    });
  });

  describe('Keycloak relay', () => {
    it('relays the Keycloak error code instead of a local OA code', () => {
      const keycloakBody = { error: 'invalid_grant', error_description: 'Invalid credentials' };
      const exception = OaErrorMapper.fromKeycloak(
        HttpStatus.UNAUTHORIZED,
        'Invalid credentials',
        keycloakBody,
      );
      const { status, body } = OaErrorMapper.toResponse(exception);

      expect(status).toBe(HttpStatus.UNAUTHORIZED);
      expect(body.error_code).toBe('invalid_grant');
      expect(body.error_stack[0]).toMatchObject({
        source: 'keycloak',
        error: 'invalid_grant',
      });
    });

    it('falls back to a local code when Keycloak sends no error field', () => {
      const exception = OaErrorMapper.fromKeycloak(HttpStatus.UNAUTHORIZED, 'boom', {});
      const { body } = OaErrorMapper.toResponse(exception);

      expect(body.error_code).toBe(OA_ERROR_CODE.UNAUTHORIZED);
    });

    it('falls back to the local default code for a non-object Keycloak body', () => {
      const exception = OaErrorMapper.fromKeycloak(
        HttpStatus.INTERNAL_SERVER_ERROR,
        'boom',
        'plain text body',
      );
      const { body } = OaErrorMapper.toResponse(exception);

      expect(body.error_code).toBe(OA_ERROR_CODE.INTERNAL);
      expect(body.error_stack[0]).toMatchObject({ source: 'keycloak', raw: 'plain text body' });
    });
  });

  describe('generic Nest exceptions', () => {
    it('formats a plain HttpException into the OA envelope', () => {
      const { status, body } = OaErrorMapper.toResponse(
        new BadRequestException('bad body'),
      );

      expect(status).toBe(HttpStatus.BAD_REQUEST);
      expect(body.error_code).toBe(OA_ERROR_CODE.BAD_REQUEST);
      expect(body.error_source).toBe(OA_ERROR_SOURCE);
      expect(Array.isArray(body.error_stack)).toBe(true);
    });

    it('passes an already-OA-shaped response through untouched', () => {
      const envelope = {
        error_code: 'OA-418',
        error_description: "I'm a teapot",
        error_source: OA_ERROR_SOURCE,
        error_stack: [{ note: 'already formatted' }],
      };
      const exception = new HttpException(envelope, 418);

      const { status, body } = OaErrorMapper.toResponse(exception);

      expect(status).toBe(418);
      expect(body).toEqual(envelope);
    });

    it('formats an HttpException carrying a plain string response', () => {
      const exception = new HttpException('plain string', HttpStatus.BAD_REQUEST);

      const { body } = OaErrorMapper.toResponse(exception);

      expect(body.error_description).toBe('plain string');
    });

    it('falls back to the internal code for a status outside the local family', () => {
      const exception = new HttpException('teapot', 418);

      const { body } = OaErrorMapper.toResponse(exception);

      expect(body.error_code).toBe(OA_ERROR_CODE.INTERNAL);
    });
  });

  describe('unknown errors', () => {
    it('maps an unexpected thrown Error to OA-500 without leaking internals as the description', () => {
      const { status, body } = OaErrorMapper.toResponse(new Error('db connection reset'));

      expect(status).toBe(HttpStatus.INTERNAL_SERVER_ERROR);
      expect(body.error_code).toBe(OA_ERROR_CODE.INTERNAL);
      expect(body.error_description).toBe('Unexpected internal error.');
      expect(body.error_stack[0]).toMatchObject({ message: 'db connection reset' });
    });

    it('maps a non-Error thrown value', () => {
      const { status, body } = OaErrorMapper.toResponse('just a string');

      expect(status).toBe(HttpStatus.INTERNAL_SERVER_ERROR);
      expect(body.error_code).toBe(OA_ERROR_CODE.INTERNAL);
    });
  });

  it('exposes OaException.toEnvelope() directly for callers that need the body without the filter', () => {
    const exception = new OaException(HttpStatus.CONFLICT, OA_ERROR_CODE.CONFLICT, 'dup');
    expect(exception.toEnvelope()).toEqual({
      error_code: OA_ERROR_CODE.CONFLICT,
      error_description: 'dup',
      error_source: OA_ERROR_SOURCE,
      error_stack: [{ error_code: OA_ERROR_CODE.CONFLICT, message: 'dup' }],
    });
  });
});
