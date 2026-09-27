package br.pucrs.constrsw.oauth.config;

import br.pucrs.constrsw.oauth.port.AuthenticationGateway;
import br.pucrs.constrsw.oauth.port.RoleGateway;
import br.pucrs.constrsw.oauth.port.RoleMappingGateway;
import br.pucrs.constrsw.oauth.port.UserGateway;
import br.pucrs.constrsw.oauth.service.LoginService;
import br.pucrs.constrsw.oauth.service.RoleMappingService;
import br.pucrs.constrsw.oauth.service.RoleService;
import br.pucrs.constrsw.oauth.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    LoginService loginService(AuthenticationGateway authenticationGateway) {
        return new LoginService(authenticationGateway);
    }

    @Bean
    UserService userService(UserGateway userGateway) {
        return new UserService(userGateway);
    }

    @Bean
    RoleService roleService(RoleGateway roleGateway) {
        return new RoleService(roleGateway);
    }

    @Bean
    RoleMappingService roleMappingService(RoleGateway roleGateway, RoleMappingGateway roleMappingGateway) {
        return new RoleMappingService(roleGateway, roleMappingGateway);
    }
}
