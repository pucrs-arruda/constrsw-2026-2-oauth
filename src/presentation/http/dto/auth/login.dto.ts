import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

export class LoginDto {
  @ApiProperty({ example: 'admin@constrsw.com' })
  @IsString()
  @IsNotEmpty({ message: 'username e obrigatorio.' })
  username: string;

  @ApiProperty({ example: 'admin123' })
  @IsString()
  @IsNotEmpty({ message: 'password e obrigatorio.' })
  password: string;
}
