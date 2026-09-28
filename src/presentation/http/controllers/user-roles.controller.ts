import {
  Controller,
  Delete,
  HttpCode,
  HttpStatus,
  Param,
  Post,
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
import { RolesService } from '../../../application/roles/roles.service';

@ApiTags('users-roles')
@ApiBearerAuth()
@UseGuards(BearerTokenGuard)
@Controller('users')
export class UserRolesController {
  constructor(private readonly rolesService: RolesService) {}

  @Post(':userId/roles/:roleId')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Atribui um role a um usuario' })
  @ApiResponse({ status: 204, description: 'No Content' })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  async assign(
    @BearerToken() token: string,
    @Param('userId') userId: string,
    @Param('roleId') roleId: string,
  ): Promise<void> {
    await this.rolesService.assignToUser(token, userId, roleId);
  }

  @Delete(':userId/roles/:roleId')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Remove a atribuicao de um role de um usuario' })
  @ApiResponse({ status: 204, description: 'No Content' })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  async unassign(
    @BearerToken() token: string,
    @Param('userId') userId: string,
    @Param('roleId') roleId: string,
  ): Promise<void> {
    await this.rolesService.removeFromUser(token, userId, roleId);
  }
}
