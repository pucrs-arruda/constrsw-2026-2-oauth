import { PartialType } from "@nestjs/swagger";
import { CreateRoleDto } from "./create-role.dto";

/**
 * Partial update (PATCH): every field optional.
 */
export class PatchRoleDto extends PartialType(CreateRoleDto) {}
