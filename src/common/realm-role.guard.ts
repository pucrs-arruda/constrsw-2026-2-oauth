import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  SetMetadata,
  UnauthorizedException,
} from "@nestjs/common";
import { Reflector } from "@nestjs/core";
import type { Request } from "express";
import { KeycloakClient } from "../auth/keycloak.client";

export const REALM_ROLE_KEY = "required-realm-role";

/**
 * Marca uma rota (ou controller) como exigindo um realm role do Keycloak. O
 * valor lido em `realm_access.roles` do token introspeccionado.
 */
export const RequireRealmRole = (role: string) =>
  SetMetadata(REALM_ROLE_KEY, role);

/**
 * Autoriza a requisição pelo token do chamador.
 *
 * Sem guard não há como confiar só na presença do header: as rotas de roles
 * falam com o Keycloak pela service account (que tem `manage-realm`), então
 * qualquer Bearer bem formado executaria a operação. Aqui o token é validado
 * no Keycloak (introspecção) — token ausente/inválido/expirado vira 401 e um
 * token válido sem o role exigido vira 403. O token nunca é usado para a
 * operação em si; ele só prova identidade e autorização.
 */
@Injectable()
export class RealmRoleGuard implements CanActivate {
  constructor(
    private readonly keycloak: KeycloakClient,
    private readonly reflector: Reflector,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const required = this.reflector.getAllAndOverride<string | undefined>(
      REALM_ROLE_KEY,
      [context.getHandler(), context.getClass()],
    );
    if (!required) return true;

    const request = context.switchToHttp().getRequest<Request>();
    const introspection = await this.keycloak.introspect(
      this.bearerToken(request.headers.authorization),
    );
    if (!introspection.active) throw new UnauthorizedException();

    const roles = introspection.realm_access?.roles ?? [];
    if (!roles.includes(required)) throw new ForbiddenException();
    return true;
  }

  private bearerToken(authorization?: string): string {
    const match = authorization?.match(/^Bearer\s+([^\s]+)$/i);
    if (!match) throw new UnauthorizedException();
    return match[1];
  }
}
