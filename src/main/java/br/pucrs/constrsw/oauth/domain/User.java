package br.pucrs.constrsw.oauth.domain;

public record User(String id, String username, String firstName, String lastName, boolean enabled) {
}
