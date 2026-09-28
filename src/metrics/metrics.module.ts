import { Global, Module } from "@nestjs/common";
import { APP_INTERCEPTOR } from "@nestjs/core";
import { HttpMetricsInterceptor } from "./http-metrics.interceptor";
import { MetricsController } from "./metrics.controller";
import { MetricsService } from "./metrics.service";

/**
 * Global porque os dois clientes do Keycloak vivem em módulos diferentes. O
 * interceptor entra via `APP_INTERCEPTOR` (não em `main.ts`) para que os testes
 * e2e, que montam o app a partir do `AppModule`, também meçam as requisições.
 */
@Global()
@Module({
  controllers: [MetricsController],
  providers: [
    MetricsService,
    { provide: APP_INTERCEPTOR, useClass: HttpMetricsInterceptor },
  ],
  exports: [MetricsService],
})
export class MetricsModule {}
