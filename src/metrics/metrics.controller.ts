import { Controller, Get, Header } from "@nestjs/common";
import { ApiExcludeController } from "@nestjs/swagger";
import { Registry } from "prom-client";
import { MetricsService } from "./metrics.service";

/** Endpoint de scrape do Prometheus — fora do Swagger por não ser API de negócio. */
@Controller("metrics")
@ApiExcludeController()
export class MetricsController {
  constructor(private readonly metrics: MetricsService) {}

  @Get()
  @Header("Content-Type", Registry.PROMETHEUS_CONTENT_TYPE)
  scrape(): Promise<string> {
    return this.metrics.registry.metrics();
  }
}
