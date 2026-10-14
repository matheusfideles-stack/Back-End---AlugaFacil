package com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida a assinatura HMAC-SHA256 que o Mercado Pago envia nos webhooks.
 * <p>
 * O provedor manda os cabecalhos {@code x-signature} (no formato {@code ts=...,v1=...})
 * e {@code x-request-id}. O manifesto assinado e
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}.
 * <p>
 * Se {@code pagamento.mercadopago.webhook-secret} estiver vazio a validacao e ignorada,
 * o que e util em desenvolvimento mas nunca deve ser feito em producao.
 */
@Component
public class MercadoPagoWebhookValidator {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookValidator.class);

    private final MercadoPagoProperties propriedades;

    public MercadoPagoWebhookValidator(MercadoPagoProperties propriedades) {
        this.propriedades = propriedades;
    }

    public boolean assinaturaValida(String xSignature, String xRequestId, String dataId) {
        if (!StringUtils.hasText(propriedades.getWebhookSecret())) {
            log.warn("Webhook do Mercado Pago recebido sem validacao de assinatura "
                    + "(pagamento.mercadopago.webhook-secret nao configurado)");
            return true;
        }
        if (!StringUtils.hasText(xSignature) || !StringUtils.hasText(dataId)) {
            return false;
        }

        String timestamp = null;
        String assinaturaRecebida = null;
        for (String parte : xSignature.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length != 2) {
                continue;
            }
            if ("ts".equals(chaveValor[0].trim())) {
                timestamp = chaveValor[1].trim();
            } else if ("v1".equals(chaveValor[0].trim())) {
                assinaturaRecebida = chaveValor[1].trim();
            }
        }
        if (timestamp == null || assinaturaRecebida == null) {
            return false;
        }

        String manifesto = "id:" + dataId.toLowerCase() + ";"
                + (StringUtils.hasText(xRequestId) ? "request-id:" + xRequestId + ";" : "")
                + "ts:" + timestamp + ";";

        String assinaturaEsperada = hmacSha256(manifesto, propriedades.getWebhookSecret());

        boolean valida = MessageDigest.isEqual(
                assinaturaEsperada.getBytes(StandardCharsets.UTF_8),
                assinaturaRecebida.toLowerCase().getBytes(StandardCharsets.UTF_8));

        if (!valida) {
            log.warn("Assinatura invalida no webhook do Mercado Pago para data.id={}", dataId);
        }
        return valida;
    }

    private String hmacSha256(String conteudo, String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular a assinatura do webhook", e);
        }
    }
}
