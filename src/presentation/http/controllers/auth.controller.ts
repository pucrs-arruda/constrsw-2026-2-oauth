import {
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  UseInterceptors,
} from '@nestjs/common';
import { NoFilesInterceptor } from '@nestjs/platform-express';
import {
  ApiBody,
  ApiConsumes,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { ErrorResponseDto } from '../dto/error-response.dto';
import { Body } from '@nestjs/common';
import { LoginDto } from '../dto/auth/login.dto';
import { TokenResponseDto } from '../dto/auth/token-response.dto';
import { AuthService } from '../../../application/auth/auth.service';

@ApiTags('login')
@Controller()
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('login')
  @HttpCode(HttpStatus.CREATED)
  @UseInterceptors(NoFilesInterceptor())
  @ApiOperation({ summary: 'Autentica um usuario no realm constrsw' })
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    schema: {
      type: 'object',
      required: ['username', 'password'],
      properties: {
        username: { type: 'string', example: 'admin@constrsw.com' },
        password: { type: 'string', example: 'admin123' },
      },
    },
  })
  @ApiResponse({ status: 201, description: 'Created', type: TokenResponseDto })
  @ApiResponse({ status: 400, description: 'Bad Request', type: ErrorResponseDto })
  @ApiResponse({ status: 401, description: 'Unauthorized', type: ErrorResponseDto })
  async login(@Body() dto: LoginDto): Promise<TokenResponseDto> {
    const token = await this.authService.login(dto.username, dto.password);
    // devolve a resposta do provedor exatamente como veio (contrato do T1)
    return token.raw as unknown as TokenResponseDto;
  }
}
