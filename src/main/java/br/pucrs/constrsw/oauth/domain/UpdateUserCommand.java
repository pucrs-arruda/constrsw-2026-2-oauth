package br.pucrs.constrsw.oauth.domain;

public record UpdateUserCommand(String firstName, String lastName, boolean enabled) {
}
