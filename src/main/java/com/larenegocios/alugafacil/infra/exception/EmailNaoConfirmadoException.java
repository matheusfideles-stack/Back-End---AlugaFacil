package com.larenegocios.alugafacil.infra.exception;

public class EmailNaoConfirmadoException extends RuntimeException {

    public EmailNaoConfirmadoException(String mensagem) {
        super(mensagem);
    }
}
