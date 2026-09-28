import { ApiProperty } from '@nestjs/swagger';

export class UserResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  username: string;

  @ApiProperty()
  'first-name': string;

  @ApiProperty()
  'last-name': string;

  @ApiProperty()
  enabled: boolean;
}
