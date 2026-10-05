import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  HttpCode,
  HttpStatus,
  Param,
  ParseBoolPipe,
  Patch,
  Post,
  Put,
  Query,
  UnauthorizedException,
} from "@nestjs/common";
import {
  ApiBadRequestResponse,
  ApiBearerAuth,
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiUnauthorizedResponse,
  ApiForbiddenResponse,
  ApiServiceUnavailableResponse,
  ApiTags,
} from "@nestjs/swagger";
import { CreateUserDto } from "./dto/create-user.dto";
import { PatchUserDto } from "./dto/patch-user.dto";
import { ReplaceUserDto } from "./dto/replace-user.dto";
import { CreatedUser } from "./interfaces/user.interface";
import { UsersService } from "./users.service";

@ApiTags("Users")
@ApiBearerAuth()
@ApiBadRequestResponse({
  description: "Corpo inválido; cada recusa vira uma entrada de error_stack.",
})
@ApiNotFoundResponse({ description: "Usuário não encontrado no realm." })
@ApiServiceUnavailableResponse({
  description: "Provedor de identidade indisponível.",
})
@Controller("users")
export class UsersController {
  constructor(private readonly users: UsersService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: "Cria um usuário" })
  @ApiCreatedResponse({ description: "Usuário criado; devolve o id." })
  @ApiConflictResponse({ description: "Username já em uso." })
  @ApiUnauthorizedResponse({ description: "Access token inválido" })
  @ApiForbiddenResponse({ description: "Permissão insuficiente" })
  create(
    @Headers("authorization") authorization: string | undefined,
    @Body() input: CreateUserDto,
  ): Promise<CreatedUser> {
    return this.users.create(this.bearerToken(authorization), input);
  }

  @Get()
  @ApiOperation({ summary: "Lista todos os usuários" })
  @ApiOkResponse({ description: "Usuários cadastrados" })
  @ApiUnauthorizedResponse({ description: "Access token inválido" })
  @ApiForbiddenResponse({ description: "Permissão insuficiente" })
  list(
    @Headers("authorization") authorization: string | undefined,
    @Query("enabled", new ParseBoolPipe({ optional: true })) enabled?: boolean,
  ) {
    return this.users.list(this.bearerToken(authorization), enabled);
  }

  @Get(":id")
  @ApiOperation({ summary: "Busca um usuário por id" })
  @ApiOkResponse({ description: "Usuário encontrado" })
  @ApiUnauthorizedResponse({ description: "Access token inválido" })
  @ApiForbiddenResponse({ description: "Permissão insuficiente" })
  get(
    @Headers("authorization") authorization: string | undefined,
    @Param("id") id?: string,
  ) {
    if (!id) throw new BadRequestException();
    return this.users.get(this.bearerToken(authorization), id);
  }

  @Put(":id")
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: "Atualiza um usuário" })
  @ApiOkResponse({ description: "Usuário atualizado" })
  @ApiConflictResponse({ description: "Username ou e-mail já em uso." })
  @ApiUnauthorizedResponse({ description: "Access token inválido" })
  @ApiForbiddenResponse({ description: "Permissão insuficiente" })
  update(
    @Headers("authorization") authorization: string | undefined,
    @Param("id") id: string,
    @Body() input: ReplaceUserDto,
  ): Promise<void> {
    return this.users.update(this.bearerToken(authorization), id, input);
  }

  @Patch(":id")
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: "Atualiza parcialmente um usuário" })
  @ApiOkResponse({ description: "Usuário atualizado" })
  @ApiConflictResponse({ description: "Username ou e-mail já em uso." })
  @ApiUnauthorizedResponse({ description: "Access token inválido" })
  @ApiForbiddenResponse({ description: "Permissão insuficiente" })
  patch(
    @Headers("authorization") authorization: string | undefined,
    @Param("id") id: string,
    @Body() input: PatchUserDto,
  ): Promise<void> {
    return this.users.patch(this.bearerToken(authorization), id, input);
  }

  @Delete(":id")
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: "Exclui logicamente um usuário" })
  @ApiNoContentResponse({
    description: "Usuário desabilitado (exclusão lógica).",
  })
  delete(
    @Headers("authorization") authorization: string | undefined,
    @Param("id") id: string,
  ): Promise<void> {
    return this.users.delete(this.bearerToken(authorization), id);
  }

  private bearerToken(authorization?: string): string {
    const match = authorization?.match(/^Bearer\s+([^\s]+)$/i);
    if (!match) throw new UnauthorizedException();
    return match[1];
  }
}
