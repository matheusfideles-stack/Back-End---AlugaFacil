package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DadosCadastroUsuario(

        @NotBlank
        String nome,

        @NotBlank
        @Email
        String email,

        @NotBlank
        String cpf,

        String telefone,

        @NotBlank
        @Size(min = 8)
        String senha,

        @NotNull
        Perfil perfil) {
}
