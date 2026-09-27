package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.UpdateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import br.pucrs.constrsw.oauth.port.UserGateway;

import java.util.List;

public class UserService {

    private final UserGateway userGateway;

    public UserService(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    public User create(String authorization, CreateUserCommand command) {
        return userGateway.create(authorization, command);
    }

    public List<User> findAll(String authorization) {
        return userGateway.findAll(authorization);
    }

    public User findById(String authorization, String id) {
        return userGateway.findById(authorization, id);
    }

    public void update(String authorization, String id, UpdateUserCommand command) {
        userGateway.update(authorization, id, command);
    }

    public void updatePassword(String authorization, String id, String password) {
        userGateway.updatePassword(authorization, id, password);
    }

    public void disable(String authorization, String id) {
        userGateway.disable(authorization, id);
    }
}
