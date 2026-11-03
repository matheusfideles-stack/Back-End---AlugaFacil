package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DadosMensagemAoProprietario(
        @NotBlank
        @Size(min = 5, max = 1000, message = "escreva a mensagem com 5 a 1000 caracteres")
        String mensagem) {
}
