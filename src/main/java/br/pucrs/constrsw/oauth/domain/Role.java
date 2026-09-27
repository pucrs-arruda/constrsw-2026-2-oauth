package br.pucrs.constrsw.oauth.domain;

import java.util.Map;

public record Role(String id, String name, String description, Map<String, Object> attributes) {

    public Role merge(Role partial) {
        String updatedName = partial.name() != null && !partial.name().isBlank()
                ? partial.name() : name;
        return new Role(id, updatedName,
                partial.description() != null ? partial.description() : description,
                partial.attributes() != null ? partial.attributes() : attributes);
    }

    public Role markedDeleted() {
        return new Role(id, "DELETED_" + name, description, attributes);
    }
}
