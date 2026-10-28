package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.usuario.Perfil;

public record DadosTokenJWT(String token, String nome, Perfil perfil) {
}
