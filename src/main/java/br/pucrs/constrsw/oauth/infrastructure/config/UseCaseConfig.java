package br.pucrs.constrsw.oauth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.pucrs.constrsw.oauth.application.port.in.AttachRoleToUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.CreateRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.CreateUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.DeleteRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.DetachRoleFromUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.DisableUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.GetRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.GetUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.ListRolesUseCase;
import br.pucrs.constrsw.oauth.application.port.in.ListUsersUseCase;
import br.pucrs.constrsw.oauth.application.port.in.LoginUseCase;
import br.pucrs.constrsw.oauth.application.port.in.ReplaceRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.UpdatePasswordUseCase;
import br.pucrs.constrsw.oauth.application.port.in.UpdateRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.UpdateUserUseCase;
import br.pucrs.constrsw.oauth.application.port.out.AuthGateway;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.application.usecase.AttachRoleToUserService;
import br.pucrs.constrsw.oauth.application.usecase.CreateRoleService;
import br.pucrs.constrsw.oauth.application.usecase.CreateUserService;
import br.pucrs.constrsw.oauth.application.usecase.DeleteRoleService;
import br.pucrs.constrsw.oauth.application.usecase.DetachRoleFromUserService;
import br.pucrs.constrsw.oauth.application.usecase.DisableUserService;
import br.pucrs.constrsw.oauth.application.usecase.GetRoleService;
import br.pucrs.constrsw.oauth.application.usecase.GetUserService;
import br.pucrs.constrsw.oauth.application.usecase.ListRolesService;
import br.pucrs.constrsw.oauth.application.usecase.ListUsersService;
import br.pucrs.constrsw.oauth.application.usecase.LoginService;
import br.pucrs.constrsw.oauth.application.usecase.ReplaceRoleService;
import br.pucrs.constrsw.oauth.application.usecase.UpdatePasswordService;
import br.pucrs.constrsw.oauth.application.usecase.UpdateRoleService;
import br.pucrs.constrsw.oauth.application.usecase.UpdateUserService;

/**
 * Composition root dos casos de uso. As classes de application/usecase sao
 * Java puro (sem anotacoes do Spring); e aqui, na infraestrutura, que elas
 * viram beans e recebem os gateways (adapters do Keycloak) por injecao.
 */
@Configuration
public class UseCaseConfig {

    // -------------------------- Auth --------------------------

    @Bean
    public LoginUseCase loginUseCase(AuthGateway authGateway) {
        return new LoginService(authGateway);
    }

    // -------------------------- Users --------------------------

    @Bean
    public CreateUserUseCase createUserUseCase(UserGateway userGateway) {
        return new CreateUserService(userGateway);
    }

    @Bean
    public ListUsersUseCase listUsersUseCase(UserGateway userGateway) {
        return new ListUsersService(userGateway);
    }

    @Bean
    public GetUserUseCase getUserUseCase(UserGateway userGateway) {
        return new GetUserService(userGateway);
    }

    @Bean
    public UpdateUserUseCase updateUserUseCase(UserGateway userGateway) {
        return new UpdateUserService(userGateway);
    }

    @Bean
    public UpdatePasswordUseCase updatePasswordUseCase(UserGateway userGateway) {
        return new UpdatePasswordService(userGateway);
    }

    @Bean
    public DisableUserUseCase disableUserUseCase(UserGateway userGateway) {
        return new DisableUserService(userGateway);
    }

    // -------------------------- Roles --------------------------

    @Bean
    public CreateRoleUseCase createRoleUseCase(RoleGateway roleGateway) {
        return new CreateRoleService(roleGateway);
    }

    @Bean
    public ListRolesUseCase listRolesUseCase(RoleGateway roleGateway) {
        return new ListRolesService(roleGateway);
    }

    @Bean
    public GetRoleUseCase getRoleUseCase(RoleGateway roleGateway) {
        return new GetRoleService(roleGateway);
    }

    @Bean
    public ReplaceRoleUseCase replaceRoleUseCase(RoleGateway roleGateway) {
        return new ReplaceRoleService(roleGateway);
    }

    @Bean
    public UpdateRoleUseCase updateRoleUseCase(RoleGateway roleGateway) {
        return new UpdateRoleService(roleGateway);
    }

    @Bean
    public DeleteRoleUseCase deleteRoleUseCase(RoleGateway roleGateway) {
        return new DeleteRoleService(roleGateway);
    }

    @Bean
    public AttachRoleToUserUseCase attachRoleToUserUseCase(RoleGateway roleGateway) {
        return new AttachRoleToUserService(roleGateway);
    }

    @Bean
    public DetachRoleFromUserUseCase detachRoleFromUserUseCase(RoleGateway roleGateway) {
        return new DetachRoleFromUserService(roleGateway);
    }
}
