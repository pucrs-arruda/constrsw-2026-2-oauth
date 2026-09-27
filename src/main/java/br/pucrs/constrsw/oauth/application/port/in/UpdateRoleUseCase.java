package br.pucrs.constrsw.oauth.application.port.in;

import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;

/** PATCH /roles/{id}: atualizacao parcial (so os campos informados mudam). */
public interface UpdateRoleUseCase {
    void execute(String bearer, String id, RoleUpdate update);
}
