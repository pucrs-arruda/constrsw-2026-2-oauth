import { Role } from '../../../domain/entities/role.entity';

export interface KeycloakRoleRepresentation {
  id?: string;
  name?: string;
  description?: string;
  composite?: boolean;
  clientRole?: boolean;
  containerId?: string;
}

export function toRole(kc: KeycloakRoleRepresentation): Role {
  return { id: kc.id ?? '', name: kc.name ?? '', description: kc.description };
}
