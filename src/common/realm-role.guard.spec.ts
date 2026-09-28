import { ExecutionContext, ForbiddenException, UnauthorizedException } from "@nestjs/common";
import { Reflector } from "@nestjs/core";
import { RealmRoleGuard } from "./realm-role.guard";

function contextWith(authorization?: string): ExecutionContext {
  return {
    getHandler: () => undefined,
    getClass: () => undefined,
    switchToHttp: () => ({
      getRequest: () => ({ headers: { authorization } }),
    }),
  } as unknown as ExecutionContext;
}

function createGuard(
  introspect: jest.Mock,
  required: string | undefined = "administrator",
) {
  const reflector = {
    getAllAndOverride: jest.fn().mockReturnValue(required),
  } as unknown as Reflector;
  return new RealmRoleGuard({ introspect } as never, reflector);
}

describe("RealmRoleGuard", () => {
  it("lets the request through when the token carries the required role", async () => {
    const introspect = jest.fn().mockResolvedValue({
      active: true,
      realm_access: { roles: ["administrator", "USER"] },
    });
    const guard = createGuard(introspect);

    await expect(guard.canActivate(contextWith("Bearer admin-token"))).resolves.toBe(
      true,
    );
    expect(introspect).toHaveBeenCalledWith("admin-token");
  });

  it("rejects a valid token missing the required realm role with 403", async () => {
    const introspect = jest.fn().mockResolvedValue({
      active: true,
      realm_access: { roles: ["student"] },
    });
    const guard = createGuard(introspect);

    await expect(
      guard.canActivate(contextWith("Bearer student-token")),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it("rejects an inactive token with 401", async () => {
    const introspect = jest.fn().mockResolvedValue({ active: false });
    const guard = createGuard(introspect);

    await expect(
      guard.canActivate(contextWith("Bearer forged")),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it("rejects a request without a bearer header before introspecting", async () => {
    const introspect = jest.fn();
    const guard = createGuard(introspect);

    await expect(guard.canActivate(contextWith(undefined))).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    expect(introspect).not.toHaveBeenCalled();
  });

  it("does not enforce anything when no role is required", async () => {
    const introspect = jest.fn();
    const reflector = {
      getAllAndOverride: jest.fn().mockReturnValue(undefined),
    } as unknown as Reflector;
    const guard = new RealmRoleGuard({ introspect } as never, reflector);

    await expect(guard.canActivate(contextWith(undefined))).resolves.toBe(true);
    expect(introspect).not.toHaveBeenCalled();
  });
});
