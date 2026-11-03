package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.avaliacao.Avaliacao;

import java.time.LocalDateTime;

public record DadosAvaliacaoAdmin(
        Long id,
        Long imovelId,
        String tituloImovel,
        String nomeAutor,
        String emailAutor,
        Integer nota,
        String comentario,
        LocalDateTime criadoEm
) {
    public DadosAvaliacaoAdmin(Avaliacao avaliacao) {
        this(avaliacao.getId(), avaliacao.getImovel().getId(), avaliacao.getImovel().getTitulo(),
                avaliacao.getUsuario().getNome(), avaliacao.getUsuario().getEmail(),
                avaliacao.getNota(), avaliacao.getComentario(), avaliacao.getCriadoEm());
    }
}
