package br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Um item do error_stack: um erro da cadeia que levou ao erro final, com o
 * sistema onde ele ocorreu (ex.: "Keycloak" ou "OAuthAPI").
 */
public class ErrorStackEntryDto {

    private static final String DEFAULT_SOURCE = "OAuthAPI";

    @JsonProperty("source")
    private String source;

    @JsonProperty("type")
    private String type;

    @JsonProperty("message")
    private String message;

    public ErrorStackEntryDto() {}

    public ErrorStackEntryDto(String type, String message) {
        this(DEFAULT_SOURCE, type, message);
    }

    public ErrorStackEntryDto(String source, String type, String message) {
        this.source = source;
        this.type = type;
        this.message = message;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
