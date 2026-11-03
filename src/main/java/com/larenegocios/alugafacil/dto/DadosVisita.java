package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.visita.StatusVisita;
import com.larenegocios.alugafacil.domain.visita.Visita;

import java.time.LocalDateTime;

public record DadosVisita(
        Long id,
        Long imovelId,
        String tituloImovel,
        Long locatarioId,
        String nomeLocatario,
        String emailLocatario,
        String telefoneLocatario,
        LocalDateTime dataHora,
        String mensagem,
        StatusVisita status,
        LocalDateTime criadoEm
) {
    public DadosVisita(Visita v) {
        this(v.getId(), v.getImovel().getId(), v.getImovel().getTitulo(), v.getLocatario().getId(),
                v.getLocatario().getNome(), v.getLocatario().getEmail(), v.getLocatario().getTelefone(),
                v.getDataHora(), v.getMensagem(), v.getStatus(), v.getCriadoEm());
    }
}
