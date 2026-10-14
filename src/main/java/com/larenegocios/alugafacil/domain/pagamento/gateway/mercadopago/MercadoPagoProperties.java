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

    private String accessToken;

    private String publicKey;

    private String baseUrl = "https://api.mercadopago.com";

    // Deixe vazio em localhost: o Mercado Pago rejeita URLs nao publicas.
    private String notificationUrl;

    private String webhookSecret;

    private int pixExpiracaoMinutos = 1440;

    private int boletoDiasMinimos = 3;

    // O Mercado Pago recusa vencimento a mais de 29 dias e conta em horas; o boleto vence 23:59, entao 28.
    private int boletoDiasMaximos = 28;

    private int timeoutSegundos = 20;
}
