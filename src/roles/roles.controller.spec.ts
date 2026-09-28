import { HttpStatus } from '@nestjs/common';

import { OaException } from '../errors';
import { KeycloakAdminService } from './keycloak-admin.service';
import { RolesController } from './roles.controller';

describe('RolesController', () => {
  let controller: RolesController;
  let admin: jest.Mocked<Pick<KeycloakAdminService, 'createRole' | 'listRoles' | 'getRole' | 'updateRole' | 'replaceRole' | 'logicalDeleteRole' | 'assignRole' | 'unassignRole'>>;

  beforeEach(() => {
    admin = {
      createRole: jest.fn(),
      listRoles: jest.fn(),
      getRole: jest.fn(),
      updateRole: jest.fn(),
      replaceRole: jest.fn(),
      logicalDeleteRole: jest.fn(),
      assignRole: jest.fn(),
      unassignRole: jest.fn(),
    };
    controller = new RolesController(admin as unknown as KeycloakAdminService);
  });

  it('creates a role with its documented body', async () => {
    const role = { id: 'role-id', name: 'professor' };
    admin.createRole.mockResolvedValue(role);

    await expect(controller.create({ name: 'professor' })).resolves.toEqual(role);
    expect(admin.createRole).toHaveBeenCalledWith({ name: 'professor' });
  });

  it('rejects an invalid create body with the OA envelope', async () => {
    let error: OaException | undefined;
    try {
      await controller.create({});
    } catch (thrown) {
      error = thrown as OaException;
    }

    expect(error).toBeInstanceOf(OaException);
    expect(error?.getStatus()).toBe(HttpStatus.BAD_REQUEST);
    expect(error?.toEnvelope()).toMatchObject({
      error_code: 'OA-400',
      error_source: 'OAuthAPI',
    });
  });

  it('pins assign and unassign to the users role paths', async () => {
    await controller.assign('11111111-1111-4111-8111-111111111111', {
      roleName: 'professor',
    });
    await controller.unassign(
      '11111111-1111-4111-8111-111111111111',
      '22222222-2222-4222-8222-222222222222',
    );

    expect(admin.assignRole).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
      'professor',
    );
    expect(admin.unassignRole).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
      '22222222-2222-4222-8222-222222222222',
    );
  });

  it('rejects malformed resource identifiers', async () => {
    expect(() => controller.get('not-a-uuid')).toThrow(
      expect.objectContaining({ status: HttpStatus.BAD_REQUEST }),
    );
  });

  it('requires a complete representation for PUT', async () => {
    await expect(controller.replace('11111111-1111-4111-8111-111111111111', {})).rejects.toMatchObject({
      status: HttpStatus.BAD_REQUEST,
    });
  });

  it('rejects an empty or array PATCH body', async () => {
    await expect(controller.update('11111111-1111-4111-8111-111111111111', {})).rejects.toMatchObject({
      status: HttpStatus.BAD_REQUEST,
    });
    await expect(
      controller.update('11111111-1111-4111-8111-111111111111', {
        attributes: [],
      }),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });

  it('rejects an unsupported field in the role body', async () => {
    await expect(
      controller.create({ name: 'professor', extra: 'nope' } as never),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });

  it('rejects a non-string description', async () => {
    await expect(
      controller.create({ name: 'professor', description: 42 } as never),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });

  it('rejects a non-string name when present', async () => {
    await expect(controller.create({ name: 42 } as never)).rejects.toMatchObject({
      status: HttpStatus.BAD_REQUEST,
    });
  });

  it('rejects array-shaped or null attributes', async () => {
    await expect(
      controller.create({ name: 'professor', attributes: null } as never),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });

  it('rejects a non-object body', async () => {
    await expect(controller.create(null as never)).rejects.toMatchObject({
      status: HttpStatus.BAD_REQUEST,
    });
  });

  it('lists roles by delegating to the admin service', () => {
    const roles = [{ id: 'role-id', name: 'professor' }];
    admin.listRoles.mockResolvedValue(roles);

    expect(controller.list()).resolves.toEqual(roles);
  });

  it('fetches a single role by id', async () => {
    const role = { id: '11111111-1111-4111-8111-111111111111', name: 'professor' };
    admin.getRole.mockResolvedValue(role);

    await expect(
      controller.get('11111111-1111-4111-8111-111111111111'),
    ).resolves.toEqual(role);
  });

  it('replaces a role with a full representation', async () => {
    await controller.replace('11111111-1111-4111-8111-111111111111', {
      name: 'coordinator',
    });

    expect(admin.replaceRole).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
      { name: 'coordinator' },
    );
  });

  it('updates a role with a partial body', async () => {
    await controller.update('11111111-1111-4111-8111-111111111111', {
      description: 'updated',
    });

    expect(admin.updateRole).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
      { description: 'updated' },
    );
  });

  it('logically deletes a role', async () => {
    await controller.remove('11111111-1111-4111-8111-111111111111');

    expect(admin.logicalDeleteRole).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
    );
  });

  it('rejects an assignment with neither roleId nor roleName', async () => {
    await expect(
      controller.assign('11111111-1111-4111-8111-111111111111', {}),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });

  it('validates roleId as a UUID when provided', async () => {
    await expect(
      controller.assign('11111111-1111-4111-8111-111111111111', {
        roleId: 'not-a-uuid',
      }),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });

  it('assigns by roleId when it is a valid UUID', async () => {
    await controller.assign('11111111-1111-4111-8111-111111111111', {
      roleId: '22222222-2222-4222-8222-222222222222',
    });

    expect(admin.assignRole).toHaveBeenCalledWith(
      '11111111-1111-4111-8111-111111111111',
      '22222222-2222-4222-8222-222222222222',
    );
  });

  it('rejects malformed user or role ids for unassign', async () => {
    await expect(
      controller.unassign('not-a-uuid', '22222222-2222-4222-8222-222222222222'),
    ).rejects.toMatchObject({ status: HttpStatus.BAD_REQUEST });
  });
});