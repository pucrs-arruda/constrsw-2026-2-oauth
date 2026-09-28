import * as http from 'http';
import * as https from 'https';

import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { HttpInstrumentation } from '@opentelemetry/instrumentation-http';

/**
 * Patches `http.Server` before `@opentelemetry/sdk-node` is imported.
 * That package loads `http` while it loads, and a patch registered after
 * that load never sees the Nest server. Jest keeps its own `http` copy, so
 * the require hook alone does not wrap the server Nest uses in tests.
 */
export const httpInstrumentation = new HttpInstrumentation();

registerInstrumentations({
  instrumentations: [httpInstrumentation],
});

for (const definition of httpInstrumentation.getModuleDefinitions()) {
  if (definition.name === 'http' && definition.patch) {
    definition.patch(http);
  } else if (definition.name === 'https' && definition.patch) {
    definition.patch(https);
  }
}
