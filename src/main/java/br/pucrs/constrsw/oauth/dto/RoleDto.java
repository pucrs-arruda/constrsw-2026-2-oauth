package br.pucrs.constrsw.oauth.dto;

import br.pucrs.constrsw.oauth.domain.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleDto {

    private String id;
    private String name;
    private String description;
    private Map<String, Object> attributes;

    public RoleDto() {
    }

    public RoleDto(String id, String name, String description, Map<String, Object> attributes) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.attributes = attributes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("attributes")
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Role toDomain() {
        return new Role(id, name, description, attributes);
    }

    public static RoleDto fromDomain(Role role) {
        if (role == null) {
            return null;
        }
        return new RoleDto(role.id(), role.name(), role.description(), role.attributes());
    }
}
