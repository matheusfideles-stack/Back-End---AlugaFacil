package com.larenegocios.alugafacil.domain.pagamento.gateway;

import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;

import java.time.LocalDateTime;

public record RespostaCobranca(
        String idTransacaoGateway,
        StatusPagamento status,
        String statusDetalhe,
        String linkPagamento,
        String pixCopiaECola,
        String pixQrCodeBase64,
        String linhaDigitavel,
        String urlAutenticacao3ds,
        LocalDateTime expiraEm
) {
}
