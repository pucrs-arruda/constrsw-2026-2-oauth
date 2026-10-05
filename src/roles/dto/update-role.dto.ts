import { CreateRoleDto } from "./create-role.dto";

/**
 * Full replacement (PUT). Same shape as creation: `name` required,
 * `description` optional.
 */
export class UpdateRoleDto extends CreateRoleDto {}
