package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;

public record DadosUsuario(
        Long id,
        String nome,
        String email,
        String cpf,
        String telefone,
        Perfil perfil,
        Boolean emailConfirmado
) {
    public DadosUsuario(Usuario usuario) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCpf(),
                usuario.getTelefone(), usuario.getPerfil(), usuario.getEmailConfirmado());
    }
}
