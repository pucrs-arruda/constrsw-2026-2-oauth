import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { collectDefaultMetrics, Histogram, Registry } from "prom-client";

const KEYCLOAK_OPERATIONS = [
  "login",
  "refresh",
  "introspect",
  "admin_token",
  "admin_api",
] as const;

export type KeycloakOperation = (typeof KEYCLOAK_OPERATIONS)[number];

/**
 * Métricas expostas em `GET /metrics`.
 *
 * Registro próprio por instância da aplicação: os testes e2e sobem um app novo
 * a cada caso, e o registro global do prom-client recusaria a métrica repetida.
 */
@Injectable()
export class MetricsService {
  readonly registry = new Registry();

  /**
   * Nome e labels no formato Micrometer, para o HighErrorRate e as consultas do
   * Prometheus em `base/infrastructure` valerem também para o oauth. O
   * prom-client não gera o `_max` do Micrometer, então a latência tem alerta
   * próprio (OAuthHighLatency).
   */
  readonly httpRequests = new Histogram({
    name: "http_server_requests_seconds",
    help: "Duração das requisições HTTP atendidas pelo oauth.",
    labelNames: ["method", "uri", "status"] as const,
    registers: [this.registry],
  });

  /**
   * `result`: `ok` (2xx), `rejected` (o Keycloak respondeu não-2xx) ou
   * `unavailable` (rede ou timeout — o caso que vira 503 para o cliente).
   */
  readonly keycloakRequests = new Histogram({
    name: "oauth_keycloak_request_duration_seconds",
    help: "Duração das chamadas ao Keycloak, por operação e resultado.",
    labelNames: ["operation", "result"] as const,
    registers: [this.registry],
  });

  constructor(config: ConfigService) {
    // Nos testes cada app religaria os monitores de event loop e GC do processo.
    if (config.get<string>("NODE_ENV") !== "test") {
      collectDefaultMetrics({ register: this.registry });
    }
    // Série nascendo em 0: sem isso o `increase()` do alerta
    // OAuthKeycloakUnavailable não enxerga a primeira falha.
    for (const operation of KEYCLOAK_OPERATIONS) {
      this.keycloakRequests.zero({ operation, result: "unavailable" });
    }
  }
}
