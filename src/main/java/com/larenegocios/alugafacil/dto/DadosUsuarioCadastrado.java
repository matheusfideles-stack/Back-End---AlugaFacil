package com.larenegocios.alugafacil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;

public record DadosUsuarioCadastrado(
        Long id,
        String nome,
        String email,
        String cpf,
        String telefone,
        Perfil perfil,
        Boolean emailConfirmado,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String codigoDeTeste
) {
    public DadosUsuarioCadastrado(Usuario usuario, String codigoDeTeste) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCpf(),
                usuario.getTelefone(), usuario.getPerfil(), usuario.getEmailConfirmado(), codigoDeTeste);
    }
}
