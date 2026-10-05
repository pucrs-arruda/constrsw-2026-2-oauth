import { ApiProperty, ApiPropertyOptional } from "@nestjs/swagger";
import { IsOptional, IsString, MinLength } from "class-validator";

export class CreateRoleDto {
  @ApiProperty({ example: "professor" })
  @IsString()
  @MinLength(1)
  name!: string;

  @ApiPropertyOptional({ example: "Docente da instituição" })
  @IsOptional()
  @IsString()
  description?: string;
}
