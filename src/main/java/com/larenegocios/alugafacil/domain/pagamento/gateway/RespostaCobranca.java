package com.larenegocios.alugafacil.domain.pagamento.gateway;

import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;

import java.time.LocalDateTime;

/**
 * Resposta padronizada do gateway de pagamento, independente do provedor real.
 *
 * @param idTransacaoGateway  identificador da cobranca no provedor externo
 * @param status              status ja traduzido para o dominio da aplicacao
 * @param statusDetalhe       detalhe cru do provedor (ex.: "accredited", "cc_rejected_bad_filled_security_code")
 * @param linkPagamento       pagina de checkout do Pix ou PDF do boleto
 * @param pixCopiaECola       codigo Pix copia e cola
 * @param pixQrCodeBase64     imagem PNG do QR Code em base64 (nao persistida)
 * @param linhaDigitavel      codigo de barras do boleto
 * @param urlAutenticacao3ds  URL de autenticacao 3DS, quando o cartao de debito exige challenge
 * @param expiraEm            momento em que a cobranca expira
 */
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
