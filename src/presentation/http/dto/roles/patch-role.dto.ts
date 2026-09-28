import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString } from 'class-validator';

export class PatchRoleDto {
  @ApiPropertyOptional({ example: 'coordenador' })
  @IsOptional()
  @IsString()
  name?: string;

  @ApiPropertyOptional({ example: 'Coordenador de curso' })
  @IsOptional()
  @IsString()
  description?: string;
}
