export interface TokenSet {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
  /** Resposta completa do provedor, devolvida como esta pela API. */
  raw: Record<string, unknown>;
}
