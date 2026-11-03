package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DadosPagamento(
        Long id,
        Long contratoId,
        String tituloImovel,
        String nomeLocatario,
        String referenciaMes,
        BigDecimal valor,
        MetodoPagamento metodo,
        StatusPagamento status,

        String statusDetalhe,

        String linkPagamento,

        String pixCopiaECola,

        String pixQrCodeBase64,

        String linhaDigitavel,

        String urlAutenticacao3ds,

        LocalDateTime expiraEm,
        LocalDate vencimento,
        LocalDateTime pagoEm,
        BigDecimal multa,
        BigDecimal juros
) {
    public DadosPagamento(Pagamento pagamento) {
        this(pagamento.getId(),
                pagamento.getContrato().getId(),
                pagamento.getContrato().getImovel().getTitulo(),
                pagamento.getContrato().getLocatario().getNome(),
                pagamento.getReferenciaMes(),
                pagamento.getValor(),
                pagamento.getMetodo(),
                pagamento.getStatus(),
                pagamento.getStatusDetalhe(),
                pagamento.getLinkPagamento(),
                pagamento.getPixCopiaECola(),
                pagamento.getPixQrCodeBase64(),
                pagamento.getLinhaDigitavel(),
                pagamento.getUrlAutenticacao3ds(),
                pagamento.getExpiraEm(),
                pagamento.getVencimento(),
                pagamento.getPagoEm(),
                pagamento.getMulta() == null ? BigDecimal.ZERO : pagamento.getMulta(),
                pagamento.getJuros() == null ? BigDecimal.ZERO : pagamento.getJuros());
    }
}
