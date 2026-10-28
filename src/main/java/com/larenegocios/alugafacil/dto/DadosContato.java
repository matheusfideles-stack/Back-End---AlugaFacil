package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DadosContato(

        @NotBlank
        @Size(max = 100)
        String nome,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(max = 150)
        String assunto,

        @NotBlank
        @Size(min = 10, max = 2000, message = "escreva a mensagem com 10 a 2000 caracteres")
        String mensagem) {
}
