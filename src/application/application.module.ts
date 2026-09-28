import { Module } from '@nestjs/common';
import { InfrastructureModule } from '../infrastructure/infrastructure.module';
import { AuthService } from './auth/auth.service';
import { RolesService } from './roles/roles.service';
import { UsersService } from './users/users.service';

@Module({
  imports: [InfrastructureModule],
  providers: [AuthService, UsersService, RolesService],
  exports: [AuthService, UsersService, RolesService],
})
export class ApplicationModule {}
