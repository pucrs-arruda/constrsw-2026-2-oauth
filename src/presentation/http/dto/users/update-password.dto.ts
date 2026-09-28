import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

export class UpdatePasswordDto {
  @ApiProperty({ example: 'novaSenha123' })
  @IsString()
  @IsNotEmpty({ message: 'password e obrigatorio.' })
  password: string;
}
