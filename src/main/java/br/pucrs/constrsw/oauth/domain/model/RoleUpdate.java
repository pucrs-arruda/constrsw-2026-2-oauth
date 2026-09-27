package br.pucrs.constrsw.oauth.domain.model;

/**
 * Value object: novos valores de um role. No PATCH (atualizacao parcial),
 * campos null nao sao alterados; no PUT (substituicao), representa o role
 * inteiro.
 */
public final class RoleUpdate {

    private final String name;
    private final String description;
    private final Boolean enabled;

    public RoleUpdate(String name, String description, Boolean enabled) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public boolean isEmpty() {
        return name == null && description == null && enabled == null;
    }
}
