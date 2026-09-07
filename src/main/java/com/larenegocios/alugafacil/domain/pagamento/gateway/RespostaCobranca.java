package com.larenegocios.alugafacil.domain.pagamento.gateway;

/**
 * Resposta padronizada do gateway de pagamento, independente do provedor real.
 *
 * @param idTransacaoGateway  identificador da cobranca no provedor externo
 * @param status              status retornado pelo provedor (ex.: "approved", "pending")
 * @param linkPagamento       link de checkout/QRCode Pix/linha digitavel do boleto, quando aplicavel
 */
public record RespostaCobranca(
        String idTransacaoGateway,
        String status,
        String linkPagamento
) {
}
