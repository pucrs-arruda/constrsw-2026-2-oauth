import { TokenSet } from '../entities/token-set.entity';

export const AUTH_GATEWAY = Symbol('AUTH_GATEWAY');

export interface AuthGateway {
  login(username: string, password: string): Promise<TokenSet>;
}
