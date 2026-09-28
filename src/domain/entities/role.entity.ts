export interface Role {
  id: string;
  name: string;
  description?: string;
}

export interface NewRole {
  name: string;
  description?: string;
}

export interface RoleChanges {
  name?: string;
  description?: string;
}
