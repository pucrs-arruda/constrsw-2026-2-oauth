package br.pucrs.constrsw.oauth.application.port.in;

import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;

/** PUT /roles/{id}: substitui o role inteiro pelos valores informados. */
public interface ReplaceRoleUseCase {
    void execute(String bearer, String id, RoleUpdate replacement);
}
