package com.larenegocios.alugafacil.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago.MercadoPagoWebhookValidator;
import com.larenegocios.alugafacil.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Recebe as notificacoes de pagamento do Mercado Pago.
 * <p>
 * O endpoint e publico (o provedor nao envia JWT), entao a autenticidade vem da
 * assinatura HMAC no cabecalho {@code x-signature}. O corpo da notificacao nunca e
 * usado como fonte de verdade: dele so aproveitamos o id, e o status real e buscado
 * na API do Mercado Pago.
 * <p>
 * Responde 200 mesmo em casos que ignoramos, para que o provedor pare de reenviar.
 */
@RestController
@RequestMapping("/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private MercadoPagoWebhookValidator validator;

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestBody(required = false) JsonNode corpo,
            @RequestParam Map<String, String> parametros,
            @RequestHeader(value = "x-signature", required = false) String assinatura,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {

        String tipo = extrairTipo(corpo, parametros);
        String idTransacao = extrairIdTransacao(corpo, parametros);

        if (!"payment".equals(tipo)) {
            log.debug("Notificacao do Mercado Pago ignorada, tipo={}", tipo);
            return ResponseEntity.ok().build();
        }
        if (!StringUtils.hasText(idTransacao)) {
            log.warn("Notificacao de pagamento sem id de transacao");
            return ResponseEntity.ok().build();
        }
        if (!validator.assinaturaValida(assinatura, requestId, idTransacao)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            pagamentoService.processarNotificacao(idTransacao);
        } catch (RuntimeException e) {
            // Devolver 5xx faz o Mercado Pago reenviar a notificacao, que e o comportamento desejado.
            log.error("Falha ao processar notificacao da transacao {}", idTransacao, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    private String extrairTipo(JsonNode corpo, Map<String, String> parametros) {
        if (corpo != null && corpo.hasNonNull("type")) {
            return corpo.get("type").asText();
        }
        // O formato IPN antigo manda o tipo em "topic" na query string.
        return parametros.getOrDefault("type", parametros.get("topic"));
    }

    private String extrairIdTransacao(JsonNode corpo, Map<String, String> parametros) {
        if (corpo != null && corpo.path("data").hasNonNull("id")) {
            return corpo.path("data").get("id").asText();
        }
        String daQuery = parametros.get("data.id");
        return StringUtils.hasText(daQuery) ? daQuery : parametros.get("id");
    }
}
