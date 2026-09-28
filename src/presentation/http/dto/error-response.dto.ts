import { ApiProperty } from '@nestjs/swagger';

export class ErrorStackEntryDto {
  @ApiProperty({ example: '401' })
  error_code: string;

  @ApiProperty({ example: 'invalid_grant: Invalid user credentials' })
  error_description: string;

  @ApiProperty({ example: 'Keycloak' })
  error_source: string;
}

export class ErrorResponseDto {
  @ApiProperty({
    example: '401',
    description:
      'Por padrao, o proprio codigo de resposta HTTP retornado (repassado do Keycloak quando o erro vem de la).',
  })
  error_code: string;

  @ApiProperty({ example: 'username e/ou password invalidos.' })
  error_description: string;

  @ApiProperty({ example: 'OAuthAPI' })
  error_source: string;

  @ApiProperty({ type: [ErrorStackEntryDto] })
  error_stack: ErrorStackEntryDto[];
}
