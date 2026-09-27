package br.pucrs.constrsw.oauth.domain;

public record AuthTokens(
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn) {
}
