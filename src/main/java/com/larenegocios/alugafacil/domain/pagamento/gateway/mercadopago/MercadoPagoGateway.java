package com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.Pagador;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGateway;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGatewayException;
import com.larenegocios.alugafacil.domain.pagamento.gateway.RespostaCobranca;
import com.larenegocios.alugafacil.domain.pagamento.gateway.SolicitacaoCobranca;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Integracao com a API de pagamentos do Mercado Pago (POST /v1/payments).
 * <p>
 * Cobre Pix, boleto bancario e cartao de credito/debito. Cartao exige um token
 * gerado no frontend com a public key: dados de cartao nunca trafegam por aqui.
 * <p>
 * Ativa quando "pagamento.gateway.provider=mercadopago".
 */
@Service
@ConditionalOnProperty(name = "pagamento.gateway.provider", havingValue = "mercadopago")
public class MercadoPagoGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGateway.class);

    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_EXPIRACAO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String PAYMENT_METHOD_PIX = "pix";
    private static final String PAYMENT_METHOD_BOLETO = "bolbradesco";

    private final MercadoPagoProperties propriedades;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MercadoPagoGateway(MercadoPagoProperties propriedades, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(propriedades.getAccessToken())) {
            throw new IllegalStateException(
                    "pagamento.mercadopago.access-token nao configurado. Defina a variavel de ambiente MP_ACCESS_TOKEN.");
        }
        this.propriedades = propriedades;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(propriedades.getTimeoutSegundos()));
        fabrica.setReadTimeout(Duration.ofSeconds(propriedades.getTimeoutSegundos()));

        this.restClient = RestClient.builder()
                .baseUrl(propriedades.getBaseUrl())
                .requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + propriedades.getAccessToken())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao) {
        ObjectNode corpo = montarCorpo(solicitacao);

        JsonNode resposta = executar(() -> restClient.post()
                .uri("/v1/payments")
                .header("X-Idempotency-Key", solicitacao.pagamentoId() + "-" + UUID.randomUUID())
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));

        log.info("Cobranca criada no Mercado Pago: pagamento={} mp_id={} status={} detalhe={}",
                solicitacao.pagamentoId(), resposta.path("id").asText(),
                resposta.path("status").asText(), resposta.path("status_detail").asText());

        return traduzir(resposta);
    }

    @Override
    public RespostaCobranca consultarCobranca(String idTransacaoGateway) {
        JsonNode resposta = executar(() -> restClient.get()
                .uri("/v1/payments/{id}", idTransacaoGateway)
                .retrieve()
                .body(JsonNode.class));
        return traduzir(resposta);
    }

    @Override
    public void estornarCobranca(String idTransacaoGateway) {
        executar(() -> restClient.post()
                .uri("/v1/payments/{id}/refunds", idTransacaoGateway)
                .header("X-Idempotency-Key", "estorno-" + idTransacaoGateway + "-" + UUID.randomUUID())
                .body(objectMapper.createObjectNode())
                .retrieve()
                .body(JsonNode.class));

        log.info("Estorno solicitado no Mercado Pago para a transacao {}", idTransacaoGateway);
    }

    // ------------------------------------------------------------------ corpo

    private ObjectNode montarCorpo(SolicitacaoCobranca solicitacao) {
        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put("transaction_amount", solicitacao.valor().setScale(2, RoundingMode.HALF_UP));
        corpo.put("description", solicitacao.descricao());
        corpo.put("external_reference", String.valueOf(solicitacao.pagamentoId()));

        if (StringUtils.hasText(propriedades.getNotificationUrl())) {
            corpo.put("notification_url", propriedades.getNotificationUrl());
        }

        corpo.set("payer", montarPagador(solicitacao));

        switch (solicitacao.metodo()) {
            case PIX -> {
                corpo.put("payment_method_id", PAYMENT_METHOD_PIX);
                corpo.put("date_of_expiration", formatarExpiracao(
                        ZonedDateTime.now(ZONA_BRASIL).plusMinutes(propriedades.getPixExpiracaoMinutos())));
            }
            case BOLETO -> {
                corpo.put("payment_method_id", PAYMENT_METHOD_BOLETO);
                corpo.put("date_of_expiration", formatarExpiracao(vencimentoBoleto(solicitacao.vencimento())));
            }
            case CARTAO_CREDITO -> {
                corpo.put("token", solicitacao.tokenCartao());
                corpo.put("installments", solicitacao.parcelas() == null ? 1 : solicitacao.parcelas());
                corpo.put("payment_method_id", solicitacao.bandeiraCartao());
                corpo.put("capture", true);
            }
            case CARTAO_DEBITO -> {
                corpo.put("token", solicitacao.tokenCartao());
                corpo.put("installments", 1);
                corpo.put("payment_method_id", solicitacao.bandeiraCartao());
                corpo.put("capture", true);
                // Debito no Brasil costuma exigir autenticacao 3DS; a resposta traz a URL do challenge.
                corpo.put("three_d_secure_mode", "optional");
            }
        }
        return corpo;
    }

    private ObjectNode montarPagador(SolicitacaoCobranca solicitacao) {
        Pagador pagador = solicitacao.pagador();

        ObjectNode no = objectMapper.createObjectNode();
        no.put("email", pagador.email());
        no.put("first_name", pagador.nome());
        no.put("last_name", pagador.sobrenome());

        ObjectNode identificacao = no.putObject("identification");
        identificacao.put("type", "CPF");
        identificacao.put("number", apenasDigitos(pagador.cpf()));

        // O boleto exige endereco completo do pagador; para os demais meios e opcional.
        if (solicitacao.metodo() == MetodoPagamento.BOLETO) {
            ObjectNode endereco = no.putObject("address");
            endereco.put("zip_code", apenasDigitos(pagador.cep()));
            endereco.put("street_name", pagador.logradouro());
            endereco.put("street_number", pagador.numero());
            endereco.put("neighborhood", pagador.bairro());
            endereco.put("city", pagador.cidade());
            endereco.put("federal_unit", pagador.uf());
        }
        return no;
    }

    /**
     * O Mercado Pago recusa boletos com vencimento a mais de 29 dias da emissao, entao o
     * vencimento do contrato e apenas o alvo: aqui ele e limitado a janela aceita.
     */
    private ZonedDateTime vencimentoBoleto(LocalDate vencimento) {
        LocalDate hoje = LocalDate.now(ZONA_BRASIL);
        LocalDate minimo = hoje.plusDays(propriedades.getBoletoDiasMinimos());
        LocalDate maximo = hoje.plusDays(propriedades.getBoletoDiasMaximos());

        LocalDate data = vencimento == null ? minimo : vencimento;
        if (data.isBefore(minimo)) {
            data = minimo;
        } else if (data.isAfter(maximo)) {
            log.info("Vencimento {} excede a janela aceita pelo Mercado Pago; boleto emitido para {}", data, maximo);
            data = maximo;
        }
        return data.atTime(23, 59, 59).atZone(ZONA_BRASIL);
    }

    private String formatarExpiracao(ZonedDateTime momento) {
        return momento.format(FORMATO_EXPIRACAO);
    }

    private String apenasDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    // --------------------------------------------------------------- resposta

    private RespostaCobranca traduzir(JsonNode resposta) {
        JsonNode dadosPix = resposta.path("point_of_interaction").path("transaction_data");

        String linkPagamento = textoOuNulo(dadosPix.path("ticket_url"));
        if (linkPagamento == null) {
            linkPagamento = textoOuNulo(resposta.path("transaction_details").path("external_resource_url"));
        }

        return new RespostaCobranca(
                textoOuNulo(resposta.path("id")),
                mapearStatus(textoOuNulo(resposta.path("status"))),
                textoOuNulo(resposta.path("status_detail")),
                linkPagamento,
                textoOuNulo(dadosPix.path("qr_code")),
                textoOuNulo(dadosPix.path("qr_code_base64")),
                textoOuNulo(resposta.path("barcode").path("content")),
                textoOuNulo(resposta.path("three_ds_info").path("external_resource_url")),
                lerExpiracao(resposta.path("date_of_expiration"))
        );
    }

    private StatusPagamento mapearStatus(String status) {
        if (status == null) {
            return StatusPagamento.PENDENTE;
        }
        return switch (status) {
            case "approved" -> StatusPagamento.PAGO;
            case "authorized", "in_process", "in_mediation" -> StatusPagamento.PROCESSANDO;
            case "pending" -> StatusPagamento.PENDENTE;
            case "rejected", "cancelled" -> StatusPagamento.RECUSADO;
            case "refunded", "charged_back" -> StatusPagamento.ESTORNADO;
            default -> {
                log.warn("Status desconhecido recebido do Mercado Pago: {}", status);
                yield StatusPagamento.PENDENTE;
            }
        };
    }

    private LocalDateTime lerExpiracao(JsonNode no) {
        String valor = textoOuNulo(no);
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel interpretar date_of_expiration recebido do Mercado Pago: {}", valor);
            return null;
        }
    }

    private String textoOuNulo(JsonNode no) {
        if (no == null || no.isMissingNode() || no.isNull()) {
            return null;
        }
        String texto = no.asText();
        return StringUtils.hasText(texto) ? texto : null;
    }

    // ------------------------------------------------------------------ erros

    private JsonNode executar(Supplier<JsonNode> chamada) {
        try {
            return chamada.get();
        } catch (RestClientResponseException e) {
            throw new PaymentGatewayException(descreverErro(e), e);
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayException("Nao foi possivel comunicar com o Mercado Pago: " + e.getMessage(), e);
        }
    }

    private String descreverErro(RestClientResponseException e) {
        String corpo = e.getResponseBodyAsString();
        log.error("Mercado Pago respondeu {}: {}", e.getStatusCode(), corpo);
        try {
            JsonNode erro = objectMapper.readTree(corpo);
            StringBuilder mensagem = new StringBuilder("Mercado Pago: ");
            mensagem.append(erro.path("message").asText(e.getStatusText()));

            JsonNode causas = erro.path("cause");
            if (causas.isArray() && !causas.isEmpty()) {
                mensagem.append(" (");
                for (int i = 0; i < causas.size(); i++) {
                    if (i > 0) {
                        mensagem.append("; ");
                    }
                    JsonNode causa = causas.get(i);
                    mensagem.append(causa.path("description").asText(causa.path("code").asText()));
                }
                mensagem.append(")");
            }
            return mensagem.toString();
        } catch (Exception falhaAoLer) {
            return "Mercado Pago respondeu " + e.getStatusCode() + ": " + corpo;
        }
    }
}
