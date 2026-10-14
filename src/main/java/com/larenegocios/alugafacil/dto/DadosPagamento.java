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
        String referenciaMes,
        BigDecimal valor,
        MetodoPagamento metodo,
        StatusPagamento status,

        /** Detalhe do provedor: explica por que um cartao foi recusado, por exemplo. */
        String statusDetalhe,

        /** Pagina do Pix ou PDF do boleto. */
        String linkPagamento,

        /** Codigo Pix copia e cola. */
        String pixCopiaECola,

        /** PNG do QR Code Pix em base64, presente apenas na resposta da criacao da cobranca. */
        String pixQrCodeBase64,

        /** Linha digitavel do boleto. */
        String linhaDigitavel,

        /** URL do challenge 3DS, quando o cartao de debito exige autenticacao. */
        String urlAutenticacao3ds,

        LocalDateTime expiraEm,
        LocalDate vencimento,
        LocalDateTime pagoEm
) {
    public DadosPagamento(Pagamento pagamento) {
        this(pagamento.getId(),
                pagamento.getContrato().getId(),
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
                pagamento.getPagoEm());
    }
}
