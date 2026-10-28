package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.manutencao.Manutencao;
import com.larenegocios.alugafacil.domain.manutencao.StatusManutencao;
import com.larenegocios.alugafacil.domain.manutencao.UrgenciaManutencao;

import java.time.LocalDateTime;

public record DadosManutencao(
        Long id,
        String protocolo,
        Long contratoId,
        String tituloImovel,
        String nomeLocatario,
        String tipo,
        UrgenciaManutencao urgencia,
        String descricao,
        StatusManutencao status,
        LocalDateTime criadoEm
) {
    public DadosManutencao(Manutencao manutencao) {
        this(manutencao.getId(), manutencao.getProtocolo(), manutencao.getContrato().getId(),
                manutencao.getContrato().getImovel().getTitulo(), manutencao.getContrato().getLocatario().getNome(),
                manutencao.getTipo(), manutencao.getUrgencia(), manutencao.getDescricao(),
                manutencao.getStatus(), manutencao.getCriadoEm());
    }
}
