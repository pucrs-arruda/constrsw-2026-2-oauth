import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, Matches } from 'class-validator';
import { EMAIL_REGEX } from '../../validators/email-rfc5322.validator';

export class CreateUserDto {
  @ApiProperty({ example: 'joao.silva@pucrs.br', description: 'username = e-mail' })
  @IsString()
  @Matches(EMAIL_REGEX, { message: 'username deve ser um e-mail valido.' })
  username: string;

  @ApiProperty({ example: 'trocarDepois123' })
  @IsString()
  @IsNotEmpty({ message: 'password e obrigatorio.' })
  password: string;

  @ApiProperty({ example: 'Joao' })
  @IsString()
  @IsNotEmpty({ message: 'first-name e obrigatorio.' })
  'first-name': string;

  @ApiProperty({ example: 'Silva' })
  @IsString()
  @IsNotEmpty({ message: 'last-name e obrigatorio.' })
  'last-name': string;
}
