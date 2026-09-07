package com.larenegocios.alugafacil.infra.exception;

public class TokenJWTInvalidoException extends RuntimeException {
    public TokenJWTInvalidoException(String message) {
        super(message);
    }
}
