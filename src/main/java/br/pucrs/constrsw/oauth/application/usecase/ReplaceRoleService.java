package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.ReplaceRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;

/**
 * Substituicao do role (PUT): name e obrigatorio, description ausente apaga a
 * descricao e enabled ausente volta ao padrao (true), como na criacao.
 */
public class ReplaceRoleService implements ReplaceRoleUseCase {

    private final RoleGateway roleGateway;

    public ReplaceRoleService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Override
    public void execute(String bearer, String id, RoleUpdate replacement) {
        if (replacement == null || replacement.getName() == null || replacement.getName().isBlank()) {
            throw new InvalidInputException("name is required");
        }
        boolean enabled = !Boolean.FALSE.equals(replacement.getEnabled());
        roleGateway.replace(bearer, id,
                new RoleUpdate(replacement.getName(), replacement.getDescription(), enabled));
    }
}
