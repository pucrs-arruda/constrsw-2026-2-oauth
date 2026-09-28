import { ApiProperty } from '@nestjs/swagger';

export class TokenResponseDto {
  @ApiProperty({ example: 'Bearer' })
  token_type: string;

  @ApiProperty()
  access_token: string;

  @ApiProperty({ example: 300 })
  expires_in: number;

  @ApiProperty()
  refresh_token: string;

  @ApiProperty({ example: 1800 })
  refresh_expires_in: number;
}
