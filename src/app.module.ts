import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { AuthModule } from "./auth/auth.module";
import { HealthController } from "./health.controller";
import { MetricsModule } from "./metrics/metrics.module";
import { RolesModule } from "./roles/roles.module";
import { UsersModule } from "./users/users.module";
import { validateEnvironment } from "./config/env.config";

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, validate: validateEnvironment }),
    MetricsModule,
    AuthModule,
    UsersModule,
    RolesModule,
  ],
  controllers: [HealthController],
})
export class AppModule {}
