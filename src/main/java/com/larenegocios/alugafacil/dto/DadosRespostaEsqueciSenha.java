package com.larenegocios.alugafacil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DadosRespostaEsqueciSenha(String mensagem, String codigoDeTeste) {
}
