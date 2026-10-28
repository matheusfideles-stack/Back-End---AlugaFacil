package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DadosEsqueciSenha(

        @NotBlank
        @Email
        String email) {
}
