import { NodeSDK } from '@opentelemetry/sdk-node';
import { PrometheusExporter } from '@opentelemetry/exporter-prometheus';
import { HttpInstrumentation } from '@opentelemetry/instrumentation-http';
import { ExpressInstrumentation } from '@opentelemetry/instrumentation-express';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

// Precisa ser importado antes de qualquer outro modulo em main.ts: a
// instrumentacao HTTP/Express so funciona se o OTel interceptar os modulos
// `http`/`express` antes deles serem exigidos pelo resto da aplicacao.
const metricsPort = Number(process.env.OAUTH_INTERNAL_METRICS_PORT ?? 9464);

const prometheusExporter = new PrometheusExporter({
  port: metricsPort,
  endpoint: '/metrics',
});

const sdk = new NodeSDK({
  resource: resourceFromAttributes({ [ATTR_SERVICE_NAME]: 'oauth' }),
  metricReaders: [prometheusExporter],
  instrumentations: [new HttpInstrumentation(), new ExpressInstrumentation()],
});

sdk.start();

for (const signal of ['SIGTERM', 'SIGINT'] as const) {
  process.on(signal, () => {
    sdk.shutdown().finally(() => process.exit(0));
  });
}
