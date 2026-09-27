package br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto;

import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body do PUT /roles/{id} (substituicao): representa o role inteiro.
 * description ausente apaga a descricao; enabled ausente vale true.
 */
public class RoleReplaceRequestDto {

    @NotBlank(message = "name is required")
    private String name;

    private String description;
    private Boolean enabled;

    public RoleReplaceRequestDto() {}

    public RoleUpdate toDomain() {
        return new RoleUpdate(name, description, enabled);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
