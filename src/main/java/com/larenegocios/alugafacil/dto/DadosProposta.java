package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.proposta.Proposta;
import com.larenegocios.alugafacil.domain.proposta.StatusProposta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DadosProposta(
        Long id,
        Long imovelId,
        String tituloImovel,
        Long locatarioId,
        String nomeLocatario,
        String emailLocatario,
        String telefoneLocatario,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer diaVencimento,
        BigDecimal valorMensal,
        String mensagem,
        StatusProposta status,
        String motivoRecusa,
        Long contratoId,
        LocalDateTime criadoEm,
        LocalDateTime respondidoEm
) {
    public DadosProposta(Proposta p) {
        this(p.getId(), p.getImovel().getId(), p.getImovel().getTitulo(),
                p.getLocatario().getId(), p.getLocatario().getNome(), p.getLocatario().getEmail(),
                p.getLocatario().getTelefone(), p.getDataInicio(), p.getDataFim(), p.getDiaVencimento(),
                p.getImovel().getValorAluguel().add(p.getImovel().getValorCondominio()),
                p.getMensagem(), p.getStatus(), p.getMotivoRecusa(),
                p.getContrato() == null ? null : p.getContrato().getId(), p.getCriadoEm(), p.getRespondidoEm());
    }
}
