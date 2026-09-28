import { Module } from '@nestjs/common';
import { ApplicationModule } from '../../application/application.module';
import { AuthController } from './controllers/auth.controller';
import { HealthController } from './controllers/health.controller';
import { RolesController } from './controllers/roles.controller';
import { UserRolesController } from './controllers/user-roles.controller';
import { UsersController } from './controllers/users.controller';

@Module({
  imports: [ApplicationModule],
  controllers: [
    AuthController,
    UsersController,
    RolesController,
    UserRolesController,
    HealthController,
  ],
})
export class PresentationModule {}
