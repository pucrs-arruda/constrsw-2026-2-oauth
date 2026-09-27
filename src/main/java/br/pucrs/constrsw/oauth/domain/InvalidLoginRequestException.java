package br.pucrs.constrsw.oauth.domain;

public class InvalidLoginRequestException extends RuntimeException {

    public InvalidLoginRequestException(String message) {
        super(message);
    }
}
