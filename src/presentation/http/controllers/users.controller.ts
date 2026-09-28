import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Post,
  Put,
  Query,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { BearerToken } from '../decorators/bearer-token.decorator';
import { ErrorResponseDto } from '../dto/error-response.dto';
import { BearerTokenGuard } from '../guards/bearer-token.guard';
import { CreateUserDto } from '../dto/users/create-user.dto';
import { ListUsersQueryDto } from '../dto/users/list-users-query.dto';
import { UpdatePasswordDto } from '../dto/users/update-password.dto';
import { UpdateUserDto } from '../dto/users/update-user.dto';
import { UserResponseDto } from '../dto/users/user-response.dto';
import { User } from '../../../domain/entities/user.entity';
import { UsersService } from '../../../application/users/users.service';

function toUserResponse(user: User): UserResponseDto {
  return {
    id: user.id,
    username: user.username,
    'first-name': user.firstName,
    'last-name': user.lastName,
    enabled: user.enabled,
  };
}

@ApiTags('users')
@ApiBearerAuth()
@UseGuards(BearerTokenGuard)
@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Cria um usuario' })
  @ApiResponse({ status: 201, type: UserResponseDto })
  @ApiResponse({ status: 400, type: ErrorResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 409, type: ErrorResponseDto })
  async create(
    @BearerToken() token: string,
    @Body() dto: CreateUserDto,
  ): Promise<UserResponseDto> {
    const user = await this.usersService.create(token, {
      username: dto.username,
      password: dto.password,
      firstName: dto['first-name'],
      lastName: dto['last-name'],
    });
    return toUserResponse(user);
  }

  @Get()
  @ApiOperation({ summary: 'Lista todos os usuarios (filtro opcional ?enabled=)' })
  @ApiResponse({ status: 200, type: [UserResponseDto] })
  @ApiResponse({ status: 400, type: ErrorResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  async findAll(
    @BearerToken() token: string,
    @Query() query: ListUsersQueryDto,
  ): Promise<UserResponseDto[]> {
    const users = await this.usersService.findAll(token, {
      enabled: query.enabled,
    });
    return users.map(toUserResponse);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Recupera um usuario pelo id' })
  @ApiResponse({ status: 200, type: UserResponseDto })
  @ApiResponse({ status: 400, type: ErrorResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  async findOne(
    @BearerToken() token: string,
    @Param('id') id: string,
  ): Promise<UserResponseDto> {
    return toUserResponse(await this.usersService.findOne(token, id));
  }

  @Put(':id')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Atualiza os atributos de um usuario' })
  @ApiResponse({ status: 200, description: 'OK (vazio)' })
  @ApiResponse({ status: 400, type: ErrorResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  async update(
    @BearerToken() token: string,
    @Param('id') id: string,
    @Body() dto: UpdateUserDto,
  ): Promise<void> {
    await this.usersService.update(token, id, {
      username: dto.username,
      firstName: dto['first-name'],
      lastName: dto['last-name'],
      enabled: dto.enabled,
    });
  }

  @Patch(':id')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Atualiza a senha de um usuario' })
  @ApiResponse({ status: 200, description: 'OK (vazio)' })
  @ApiResponse({ status: 400, type: ErrorResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  async updatePassword(
    @BearerToken() token: string,
    @Param('id') id: string,
    @Body() dto: UpdatePasswordDto,
  ): Promise<void> {
    await this.usersService.updatePassword(token, id, dto.password);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Exclusao logica (desabilita) de um usuario' })
  @ApiResponse({ status: 204, description: 'No Content' })
  @ApiResponse({ status: 400, type: ErrorResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  async remove(
    @BearerToken() token: string,
    @Param('id') id: string,
  ): Promise<void> {
    await this.usersService.disable(token, id);
  }
}
