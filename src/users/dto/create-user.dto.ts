import { ApiProperty } from "@nestjs/swagger";
import { IsString, Matches, MaxLength, MinLength } from "class-validator";
import { RFC_5322_EMAIL, RFC_5322_USERNAME_MESSAGE } from "./rfc5322";
import { Trim } from "./trim";

export class CreateUserDto {
  @ApiProperty({ example: "ana.souza@pucrs.br" })
  @Trim()
  @IsString({ message: "username é obrigatório" })
  @MinLength(1, { message: "username não pode ser vazio" })
  @Matches(RFC_5322_EMAIL, { message: RFC_5322_USERNAME_MESSAGE })
  @MaxLength(255, { message: "username deve ter no máximo 255 caracteres" })
  username!: string;

  @ApiProperty({ example: "Ana" })
  @Trim()
  @IsString({ message: "first-name é obrigatório" })
  @MinLength(1, { message: "first-name não pode ser vazio" })
  @MaxLength(255, { message: "first-name deve ter no máximo 255 caracteres" })
  "first-name"!: string;

  @ApiProperty({ example: "Souza" })
  @Trim()
  @IsString({ message: "last-name é obrigatório" })
  @MinLength(1, { message: "last-name não pode ser vazio" })
  @MaxLength(255, { message: "last-name deve ter no máximo 255 caracteres" })
  "last-name"!: string;

  @ApiProperty({ example: "senha-segura", minLength: 6 })
  @IsString({ message: "password é obrigatório" })
  @MinLength(6, { message: "password deve ter ao menos 6 caracteres" })
  password!: string;
}
