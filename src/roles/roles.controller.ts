import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  Patch,
  Post,
  Put,
  UseGuards,
} from "@nestjs/common";
import {
  ApiBadRequestResponse,
  ApiBearerAuth,
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiForbiddenResponse,
  ApiNoContentResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiUnauthorizedResponse,
} from "@nestjs/swagger";
import { RealmRoleGuard, RequireRealmRole } from "../common/realm-role.guard";
import { CreateRoleDto } from "./dto/create-role.dto";
import { PatchRoleDto } from "./dto/patch-role.dto";
import { UpdateRoleDto } from "./dto/update-role.dto";
import { RoleResponse } from "./interfaces/role-response.interface";
import { RolesService } from "./roles.service";

@ApiTags("Roles")
@ApiBearerAuth()
@ApiUnauthorizedResponse({ description: "Access token ausente ou inválido" })
@ApiForbiddenResponse({ description: "Apenas o perfil administrator" })
@UseGuards(RealmRoleGuard)
@RequireRealmRole("administrator")
@Controller("roles")
export class RolesController {
  constructor(private readonly roles: RolesService) {}

  @Post()
  @ApiOperation({ summary: "Cria um role" })
  @ApiCreatedResponse({ description: "Role criado" })
  @ApiBadRequestResponse({ description: "Payload inválido" })
  @ApiConflictResponse({ description: "Role já existente" })
  create(@Body() dto: CreateRoleDto): Promise<RoleResponse> {
    return this.roles.create(dto);
  }

  @Get()
  @ApiOperation({ summary: "Lista todos os roles" })
  @ApiOkResponse({ description: "Roles cadastrados" })
  findAll(): Promise<RoleResponse[]> {
    return this.roles.findAll();
  }

  @Get(":id")
  @ApiOperation({ summary: "Busca um role por id" })
  @ApiOkResponse({ description: "Role encontrado" })
  @ApiNotFoundResponse({ description: "Role não encontrado" })
  findOne(@Param("id") id: string): Promise<RoleResponse> {
    return this.roles.findOne(id);
  }

  @Put(":id")
  @ApiOperation({ summary: "Atualiza um role" })
  @ApiOkResponse({ description: "Role atualizado" })
  @ApiBadRequestResponse({ description: "Payload inválido" })
  @ApiNotFoundResponse({ description: "Role não encontrado" })
  update(
    @Param("id") id: string,
    @Body() dto: UpdateRoleDto,
  ): Promise<RoleResponse> {
    return this.roles.update(id, dto);
  }

  @Patch(":id")
  @ApiOperation({ summary: "Atualiza parcialmente um role" })
  @ApiOkResponse({ description: "Role atualizado" })
  @ApiBadRequestResponse({ description: "Payload inválido" })
  @ApiNotFoundResponse({ description: "Role não encontrado" })
  patch(
    @Param("id") id: string,
    @Body() dto: PatchRoleDto,
  ): Promise<RoleResponse> {
    return this.roles.patch(id, dto);
  }

  @Delete(":id")
  @HttpCode(204)
  @ApiOperation({ summary: "Exclui logicamente um role" })
  @ApiNoContentResponse({ description: "Role desabilitado" })
  @ApiNotFoundResponse({ description: "Role não encontrado" })
  delete(@Param("id") id: string): Promise<void> {
    return this.roles.delete(id);
  }

  @Post(":id/users/:userId")
  @HttpCode(204)
  @ApiOperation({ summary: "Atribui um role a um usuário" })
  @ApiNoContentResponse({ description: "Role atribuído" })
  @ApiNotFoundResponse({ description: "Role ou usuário não encontrado" })
  assign(
    @Param("id") id: string,
    @Param("userId") userId: string,
  ): Promise<void> {
    return this.roles.assignToUser(id, userId);
  }

  @Delete(":id/users/:userId")
  @HttpCode(204)
  @ApiOperation({ summary: "Remove um role de um usuário" })
  @ApiNoContentResponse({ description: "Atribuição removida" })
  @ApiNotFoundResponse({ description: "Role ou usuário não encontrado" })
  unassign(
    @Param("id") id: string,
    @Param("userId") userId: string,
  ): Promise<void> {
    return this.roles.removeFromUser(id, userId);
  }
}
