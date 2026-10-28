package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.avaliacao.Avaliacao;

import java.time.LocalDateTime;

public record DadosAvaliacao(
        Long id,
        Long imovelId,
        Long usuarioId,
        String nomeUsuario,
        Integer nota,
        String comentario,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public DadosAvaliacao(Avaliacao avaliacao) {
        this(avaliacao.getId(), avaliacao.getImovel().getId(), avaliacao.getUsuario().getId(),
                nomeParaExibir(avaliacao.getUsuario().getNome()), avaliacao.getNota(), avaliacao.getComentario(),
                avaliacao.getCriadoEm(), avaliacao.getAtualizadoEm());
    }

    // Avaliacao e publica: mostra o primeiro nome e a inicial do ultimo, sem expor o nome completo.
    static String nomeParaExibir(String nome) {
        String[] partes = nome == null ? new String[0] : nome.trim().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) {
            return "Usuario";
        }
        if (partes.length == 1) {
            return partes[0];
        }
        return partes[0] + " " + Character.toUpperCase(partes[partes.length - 1].charAt(0)) + ".";
    }
}
