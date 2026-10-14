package com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pagamento.mercadopago")
@Getter
@Setter
public class MercadoPagoProperties {

    /** Access token da aplicacao (TEST-... em sandbox, APP_USR-... em producao). */
    private String accessToken;

    /** Public key, usada pelo frontend para tokenizar o cartao. Nunca tokenize cartao no backend. */
    private String publicKey;

    private String baseUrl = "https://api.mercadopago.com";

    /** URL publica que recebe os webhooks. Deixe vazio em localhost: o Mercado Pago rejeita URLs nao publicas. */
    private String notificationUrl;

    /** Assinatura secreta do webhook (painel do Mercado Pago). Se vazia, a validacao e ignorada. */
    private String webhookSecret;

    /** Tempo de vida do QR Code Pix, em minutos. */
    private int pixExpiracaoMinutos = 1440;

    /** Prazo minimo do boleto quando o vencimento do contrato ja passou. */
    private int boletoDiasMinimos = 3;

    /**
     * Prazo maximo do boleto. O Mercado Pago recusa vencimentos a mais de 29 dias da
     * emissao e conta a diferenca em horas; como o boleto vence as 23:59, 28 dias deixa
     * a margem necessaria.
     */
    private int boletoDiasMaximos = 28;

    /** Timeout de conexao e leitura das chamadas HTTP, em segundos. */
    private int timeoutSegundos = 20;
}
