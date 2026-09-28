import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from "@nestjs/common";
import type { Request, Response } from "express";
import { Observable } from "rxjs";
import { MetricsService } from "./metrics.service";

/** Scrapes e healthchecks afogariam o tráfego real nos gráficos. */
const IGNORED_ROUTES = new Set(["/metrics", "/health"]);

@Injectable()
export class HttpMetricsInterceptor implements NestInterceptor {
  constructor(private readonly metrics: MetricsService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const http = context.switchToHttp();
    const request = http.getRequest<Request>();
    const response = http.getResponse<Response>();

    // O padrão da rota (`/roles/:id`), nunca o path cru: um id por série
    // explodiria a cardinalidade no Prometheus.
    const uri = (request.route as { path?: string } | undefined)?.path;
    if (!uri || IGNORED_ROUTES.has(uri)) return next.handle();

    const end = this.metrics.httpRequests.startTimer({
      method: request.method,
      uri,
    });
    // `finish` enxerga o status final, inclusive o escrito pelo ProblemDetailsFilter.
    response.once("finish", () => {
      end({ status: String(response.statusCode) });
    });
    return next.handle();
  }
}
