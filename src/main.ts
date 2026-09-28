import './telemetry/register';

import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';

import { AppModule } from './app.module';
import { KeycloakSettingsService } from './config';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);
  const settings = app.get(KeycloakSettingsService);

  // 0.0.0.0 so the compose healthcheck can reach the port from outside the
  // process namespace; the OA filter is registered in AppModule.
  await app.listen(settings.internalApiPort, '0.0.0.0');

  Logger.log(
    `oauth listening on port ${settings.internalApiPort} — ` +
      `Keycloak realm "${settings.realm}" at ${settings.serverUrl}`,
    'Bootstrap',
  );
}

void bootstrap();
