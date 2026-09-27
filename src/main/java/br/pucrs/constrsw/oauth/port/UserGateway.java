package br.pucrs.constrsw.oauth.port;

import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.UpdateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import java.util.List;

public interface UserGateway {
    User create(String authorization, CreateUserCommand command);
    List<User> findAll(String authorization);
    User findById(String authorization, String id);
    void update(String authorization, String id, UpdateUserCommand command);
    void updatePassword(String authorization, String id, String password);
    void disable(String authorization, String id);
}
