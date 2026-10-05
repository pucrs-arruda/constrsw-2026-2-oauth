import { ApiPropertyOptional } from "@nestjs/swagger";
import {
  IsString,
  Matches,
  MaxLength,
  MinLength,
  ValidateIf,
} from "class-validator";
import { RFC_5322_EMAIL, RFC_5322_USERNAME_MESSAGE } from "./rfc5322";
import { Trim } from "./trim";

/**
 * Alteração parcial: todo campo é opcional, mas ao menos um é obrigatório —
 * o corpo vazio é recusado no serviço.
 *
 * `@ValidateIf` no lugar de `@IsOptional` de propósito: `@IsOptional` também
 * ignora `null`, então `{"password": null}` escaparia do `@MinLength` e
 * chegaria cru ao Keycloak.
 *
 * Sem `enabled`: como em `CreateUserDto`/`ReplaceUserDto`, o estado de
 * habilitação é server-side e não é aceito como entrada.
 */
export class PatchUserDto {
  @ApiPropertyOptional({ example: "ana.souza@pucrs.br" })
  @ValidateIf((_object, value) => value !== undefined)
  @Trim()
  @IsString({ message: "username deve ser texto" })
  @MinLength(1, { message: "username não pode ser vazio" })
  @Matches(RFC_5322_EMAIL, { message: RFC_5322_USERNAME_MESSAGE })
  @MaxLength(255, { message: "username deve ter no máximo 255 caracteres" })
  username?: string;

  @ApiPropertyOptional({ example: "Ana" })
  @ValidateIf((_object, value) => value !== undefined)
  @Trim()
  @IsString({ message: "first-name deve ser texto" })
  @MinLength(1, { message: "first-name não pode ser vazio" })
  @MaxLength(255, { message: "first-name deve ter no máximo 255 caracteres" })
  "first-name"?: string;

  @ApiPropertyOptional({ example: "Souza" })
  @ValidateIf((_object, value) => value !== undefined)
  @Trim()
  @IsString({ message: "last-name deve ser texto" })
  @MinLength(1, { message: "last-name não pode ser vazio" })
  @MaxLength(255, { message: "last-name deve ter no máximo 255 caracteres" })
  "last-name"?: string;

  @ApiPropertyOptional({ example: "senha-nova", minLength: 6 })
  @ValidateIf((_object, value) => value !== undefined)
  @IsString({ message: "password deve ser texto" })
  @MinLength(6, { message: "password deve ter ao menos 6 caracteres" })
  password?: string;
}
