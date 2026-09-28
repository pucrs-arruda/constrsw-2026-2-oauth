import './infrastructure/telemetry/tracing';

import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { OAuthExceptionFilter } from './presentation/http/filters/oauth-exception.filter';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: false,
    }),
  );
  app.useGlobalFilters(new OAuthExceptionFilter());

  const config = new DocumentBuilder()
    .setTitle('oauth API - constrsw-2026-2 (grupo03)')
    .setDescription(
      'API REST que consome a REST API do Keycloak para autenticacao/autorizacao (T1).',
    )
    .setVersion('1.0')
    .addBearerAuth()
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('swagger', app, document);

  const port = process.env.OAUTH_INTERNAL_API_PORT ?? process.env.PORT ?? 3001;
  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`oauth API rodando em http://localhost:${port} (swagger em /swagger)`);
}
bootstrap();
