/** Resposta pública das operações de usuário. */
export interface CreatedUser {
  id: string;
  username: string;
  "first-name": string;
  "last-name": string;
  enabled: boolean;
}

/** Subconjunto da UserRepresentation do Keycloak que a trilha B escreve. */
export interface KeycloakUserRepresentation {
  username?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
  emailVerified?: boolean;
  credentials?: KeycloakCredential[];
}

export interface KeycloakCredential {
  type: "password";
  value: string;
  temporary: boolean;
}
