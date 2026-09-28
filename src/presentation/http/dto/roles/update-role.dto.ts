import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsNotEmpty, IsOptional, IsString } from 'class-validator';

export class UpdateRoleDto {
  @ApiProperty({ example: 'coordenador' })
  @IsString()
  @IsNotEmpty({ message: 'name e obrigatorio.' })
  name: string;

  @ApiPropertyOptional({ example: 'Coordenador de curso' })
  @IsOptional()
  @IsString()
  description?: string;
}
