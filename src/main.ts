import { ValidationPipe } from "@nestjs/common";
import { NestFactory } from "@nestjs/core";
import { ConfigService } from "@nestjs/config";
import { DocumentBuilder, SwaggerModule } from "@nestjs/swagger";
import cookieParser from "cookie-parser";
import { AppModule } from "./app.module";
import { ProblemDetailsFilter } from "./common/problem-details.filter";

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.use(cookieParser());
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
  app.useGlobalFilters(new ProblemDetailsFilter());

  const config = app.get(ConfigService);
  const swaggerConfig = new DocumentBuilder()
    .setTitle("ConstrSW OAuth")
    .setDescription("Gateway autocontido de identidade institucional")
    .setVersion("0.1.0")
    .addCookieAuth(config.getOrThrow<string>("SESSION_COOKIE_NAME"))
    // Esquema "bearer" referenciado pelo @ApiBearerAuth() de /users e /roles.
    .addBearerAuth()
    .build();
  SwaggerModule.setup(
    "docs",
    app,
    SwaggerModule.createDocument(app, swaggerConfig),
  );

  await app.listen(config.getOrThrow<number>("PORT"));
}

void bootstrap();
