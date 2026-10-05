import { ApiProperty } from '@nestjs/swagger';
import { IsEmail, IsString, MinLength } from 'class-validator';

export class LoginDto {
  @ApiProperty({ example: 'usuario@pucrs.br' })
  @IsEmail()
  username!: string;

  @ApiProperty({ example: 'senha-segura' })
  @IsString()
  @MinLength(1)
  password!: string;
}