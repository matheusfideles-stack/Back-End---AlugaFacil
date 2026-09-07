package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DadosContrato(
        Long id,
        Long imovelId,
        String tituloImovel,
        Long locatarioId,
        String nomeLocatario,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer diaVencimento,
        BigDecimal valorMensal,
        StatusContrato status
) {
    public DadosContrato(Contrato contrato) {
        this(contrato.getId(), contrato.getImovel().getId(), contrato.getImovel().getTitulo(),
                contrato.getLocatario().getId(), contrato.getLocatario().getNome(),
                contrato.getDataInicio(), contrato.getDataFim(), contrato.getDiaVencimento(),
                contrato.getValorMensal(), contrato.getStatus());
    }
}
