import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional, IsString, Matches } from 'class-validator';
import { EMAIL_REGEX } from '../../validators/email-rfc5322.validator';

export class UpdateUserDto {
  @ApiPropertyOptional({ example: 'joao.silva@pucrs.br' })
  @IsOptional()
  @IsString()
  @Matches(EMAIL_REGEX, { message: 'username deve ser um e-mail valido.' })
  username?: string;

  @ApiPropertyOptional({ example: 'Joao' })
  @IsOptional()
  @IsString()
  'first-name'?: string;

  @ApiPropertyOptional({ example: 'Silva' })
  @IsOptional()
  @IsString()
  'last-name'?: string;

  @ApiPropertyOptional({ example: true })
  @IsOptional()
  @IsBoolean()
  enabled?: boolean;
}
