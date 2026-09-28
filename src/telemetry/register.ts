import { httpInstrumentation } from './instrument';

import { PrometheusExporter } from '@opentelemetry/exporter-prometheus';
import { NodeSDK } from '@opentelemetry/sdk-node';

import { readInternalMetricsPort } from '../config/keycloak.config';

let sdk: NodeSDK | undefined;

/**
 * Starts the process-wide SDK. Import this module before any Nest import so
 * HTTP instrumentation is in place before the API server is created.
 * The Prometheus exporter is the only listener on the metrics port.
 */
export function startTelemetry(): void {
  if (sdk) {
    return;
  }

  sdk = new NodeSDK({
    serviceName: 'oauth',
    metricReaders: [
      new PrometheusExporter({
        host: '0.0.0.0',
        port: readInternalMetricsPort(),
        endpoint: '/metrics',
      }),
    ],
    instrumentations: [httpInstrumentation],
    // Empty lists skip the SDK default, which would push OTLP traces and logs.
    spanProcessors: [],
    logRecordProcessors: [],
  });
  sdk.start();
}

export async function stopTelemetry(): Promise<void> {
  const current = sdk;
  if (!current) {
    return;
  }
  sdk = undefined;
  await current.shutdown();
}

startTelemetry();

process.once('SIGTERM', () => {
  void stopTelemetry();
});
