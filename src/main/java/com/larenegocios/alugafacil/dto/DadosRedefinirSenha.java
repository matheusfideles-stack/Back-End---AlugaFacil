package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DadosRedefinirSenha(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "o codigo tem 6 digitos")
        String codigo,

        @NotBlank
        @Size(min = 8, message = "a senha precisa ter pelo menos 8 caracteres")
        String novaSenha) {
}
