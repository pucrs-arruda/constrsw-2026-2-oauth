package br.pucrs.constrsw.oauth.domain;

public record CreateUserCommand(String username, String password, String firstName, String lastName) {
}
