import { NewUser, User, UserChanges } from '../../../domain/entities/user.entity';

export interface KeycloakUserRepresentation {
  id?: string;
  username?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
}

export function toUser(kc: KeycloakUserRepresentation): User {
  return {
    id: kc.id ?? '',
    username: kc.username ?? '',
    firstName: kc.firstName ?? '',
    lastName: kc.lastName ?? '',
    enabled: kc.enabled ?? false,
  };
}

/** username = e-mail no realm; a senha definitiva (nao temporaria) vai em credentials. */
export function toKeycloakNewUser(data: NewUser) {
  return {
    username: data.username,
    email: data.username,
    enabled: true,
    firstName: data.firstName,
    lastName: data.lastName,
    credentials: [{ type: 'password', value: data.password, temporary: false }],
  };
}

export function toKeycloakUserChanges(
  changes: UserChanges,
): KeycloakUserRepresentation {
  const payload: KeycloakUserRepresentation = {};
  if (changes.username !== undefined) {
    payload.username = changes.username;
    payload.email = changes.username;
  }
  if (changes.firstName !== undefined) payload.firstName = changes.firstName;
  if (changes.lastName !== undefined) payload.lastName = changes.lastName;
  if (changes.enabled !== undefined) payload.enabled = changes.enabled;
  return payload;
}
