export interface User {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
}

export interface NewUser {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface UserChanges {
  username?: string;
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
}

export interface UserFilter {
  enabled?: boolean;
}
