import { Controller, Get } from "@nestjs/common";
import { ApiOkResponse, ApiOperation, ApiTags } from "@nestjs/swagger";

interface HealthResponse {
  status: "ok";
  service: "oauth";
}

@Controller("health")
@ApiTags("Health")
export class HealthController {
  @Get()
  @ApiOperation({ summary: "Verifica a saúde da API" })
  @ApiOkResponse({ description: "API disponível" })
  check(): HealthResponse {
    return { status: "ok", service: "oauth" };
  }
}
