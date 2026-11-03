package com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.Pagador;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGatewayException;
import com.larenegocios.alugafacil.domain.pagamento.gateway.RespostaCobranca;
import com.larenegocios.alugafacil.domain.pagamento.gateway.SolicitacaoCobranca;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// O Mercado Pago de mentira e um servidor HTTP local: o teste enxerga exatamente o que o sistema envia e responde
// com o que a API real responde, sem rede, sem conta e sem cobrar nada.
class MercadoPagoGatewayTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer servidor;
    private MercadoPagoProperties propriedades;

    private String metodoRecebido;
    private String caminhoRecebido;
    private String autorizacaoRecebida;
    private String chaveDeIdempotenciaRecebida;
    private JsonNode corpoRecebido;
    private int statusDaResposta;
    private String corpoDaResposta;

    @BeforeEach
    void subirServidor() throws IOException {
        statusDaResposta = 201;
        corpoDaResposta = "{\"id\":123,\"status\":\"approved\",\"status_detail\":\"accredited\"}";

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            metodoRecebido = troca.getRequestMethod();
            caminhoRecebido = troca.getRequestURI().getPath();
            autorizacaoRecebida = troca.getRequestHeaders().getFirst("Authorization");
            chaveDeIdempotenciaRecebida = troca.getRequestHeaders().getFirst("X-Idempotency-Key");
            byte[] entrada = troca.getRequestBody().readAllBytes();
            corpoRecebido = entrada.length == 0 ? null : JSON.readTree(entrada);

            byte[] saida = corpoDaResposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(statusDaResposta, saida.length);
            troca.getResponseBody().write(saida);
            troca.close();
        });
        servidor.start();

        propriedades = new MercadoPagoProperties();
        propriedades.setAccessToken("APP_USR-token-de-teste");
        propriedades.setBaseUrl("http://127.0.0.1:" + servidor.getAddress().getPort());
        propriedades.setTimeoutSegundos(5);
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private MercadoPagoGateway gateway() {
        return new MercadoPagoGateway(propriedades, JSON);
    }

    private static Pagador pagador() {
        return new Pagador("Maria", "Souza", "maria@teste.com", "123.456.789-00", "50000-000",
                "Rua A", "12", "Centro", "Recife", "PE");
    }

    private static SolicitacaoCobranca pedido(MetodoPagamento metodo, LocalDate vencimento, String token, Integer parcelas,
                                              String bandeira) {
        return new SolicitacaoCobranca(7L, new BigDecimal("1800.5"), metodo, "Aluguel Casa - 2026-12", pagador(),
                vencimento, token, parcelas, bandeira);
    }

    private static SolicitacaoCobranca pix() {
        return pedido(MetodoPagamento.PIX, LocalDate.now().plusDays(5), null, null, null);
    }

    @Test
    void semAccessTokenOGatewayNaoSobe() {
        propriedades.setAccessToken(" ");

        var erro = assertThrows(IllegalStateException.class, this::gateway);

        assertTrue(erro.getMessage().contains("MP_ACCESS_TOKEN"));
    }

    @Test
    void pixVaiParaApiDePagamentosComTokenDoServidorEChaveDeIdempotencia() {
        gateway().criarCobranca(pix());

        assertEquals("POST", metodoRecebido);
        assertEquals("/v1/payments", caminhoRecebido);
        assertEquals("Bearer APP_USR-token-de-teste", autorizacaoRecebida);
        assertTrue(chaveDeIdempotenciaRecebida.startsWith("7-"));
    }

    @Test
    void pixEnviaValorComDuasCasasReferenciaExternaECpfSoComDigitos() {
        gateway().criarCobranca(pix());

        assertEquals(0, new BigDecimal("1800.50").compareTo(corpoRecebido.get("transaction_amount").decimalValue()));
        assertEquals("7", corpoRecebido.get("external_reference").asText());
        assertEquals("pix", corpoRecebido.get("payment_method_id").asText());
        assertEquals("12345678900", corpoRecebido.at("/payer/identification/number").asText());
        assertEquals("CPF", corpoRecebido.at("/payer/identification/type").asText());
        assertEquals("Maria", corpoRecebido.at("/payer/first_name").asText());
        assertEquals("Souza", corpoRecebido.at("/payer/last_name").asText());
        assertTrue(corpoRecebido.has("date_of_expiration"));
    }

    @Test
    void pixNaoEnviaEnderecoNemDadosDeCartao() {
        gateway().criarCobranca(pix());

        assertFalse(corpoRecebido.at("/payer").has("address"));
        assertFalse(corpoRecebido.has("token"));
        assertFalse(corpoRecebido.has("installments"));
    }

    @Test
    void cadaChamadaUsaUmaChaveDeIdempotenciaDiferente() {
        gateway().criarCobranca(pix());
        String primeira = chaveDeIdempotenciaRecebida;
        gateway().criarCobranca(pix());

        assertTrue(!primeira.equals(chaveDeIdempotenciaRecebida));
    }

    @Test
    void urlDeNotificacaoSoVaiQuandoConfigurada() {
        gateway().criarCobranca(pix());
        assertFalse(corpoRecebido.has("notification_url"));

        propriedades.setNotificationUrl("https://exemplo.com/webhooks/mercadopago");
        gateway().criarCobranca(pix());
        assertEquals("https://exemplo.com/webhooks/mercadopago", corpoRecebido.get("notification_url").asText());
    }

    @Test
    void boletoEnviaEnderecoCompletoDoPagador() {
        gateway().criarCobranca(pedido(MetodoPagamento.BOLETO, LocalDate.now().plusDays(10), null, null, null));

        assertEquals("bolbradesco", corpoRecebido.get("payment_method_id").asText());
        assertEquals("50000000", corpoRecebido.at("/payer/address/zip_code").asText());
        assertEquals("Rua A", corpoRecebido.at("/payer/address/street_name").asText());
        assertEquals("12", corpoRecebido.at("/payer/address/street_number").asText());
        assertEquals("Centro", corpoRecebido.at("/payer/address/neighborhood").asText());
        assertEquals("Recife", corpoRecebido.at("/payer/address/city").asText());
        assertEquals("PE", corpoRecebido.at("/payer/address/federal_unit").asText());
    }

    private LocalDate vencimentoEnviado() {
        return OffsetDateTime.parse(corpoRecebido.get("date_of_expiration").asText())
                .atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDate();
    }

    @Test
    void boletoUsaOVencimentoDoContratoQuandoEstaDentroDaJanela() {
        LocalDate dia = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10);

        gateway().criarCobranca(pedido(MetodoPagamento.BOLETO, dia, null, null, null));

        assertEquals(dia, vencimentoEnviado());
    }

    @Test
    void boletoVencidoOuProximoDemaisAvancaParaOMinimoDeTresDias() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        gateway().criarCobranca(pedido(MetodoPagamento.BOLETO, hoje.minusDays(5), null, null, null));

        assertEquals(hoje.plusDays(3), vencimentoEnviado());
    }

    @Test
    void boletoComVencimentoMuitoLongeFicaNoLimiteDoMercadoPago() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        gateway().criarCobranca(pedido(MetodoPagamento.BOLETO, hoje.plusDays(90), null, null, null));

        assertEquals(hoje.plusDays(28), vencimentoEnviado());
    }

    @Test
    void creditoEnviaTokenParcelasEBandeira() {
        gateway().criarCobranca(pedido(MetodoPagamento.CARTAO_CREDITO, null, "tok_abc", 6, "visa"));

        assertEquals("tok_abc", corpoRecebido.get("token").asText());
        assertEquals(6, corpoRecebido.get("installments").asInt());
        assertEquals("visa", corpoRecebido.get("payment_method_id").asText());
        assertTrue(corpoRecebido.get("capture").asBoolean());
        assertFalse(corpoRecebido.has("three_d_secure_mode"));
    }

    @Test
    void creditoSemParcelasCobraEmUmaVez() {
        gateway().criarCobranca(pedido(MetodoPagamento.CARTAO_CREDITO, null, "tok_abc", null, "master"));

        assertEquals(1, corpoRecebido.get("installments").asInt());
    }

    @Test
    void debitoSempreEmUmaParcelaComAutenticacao3dsOpcional() {
        gateway().criarCobranca(pedido(MetodoPagamento.CARTAO_DEBITO, null, "tok_abc", 3, "debmaster"));

        assertEquals(1, corpoRecebido.get("installments").asInt());
        assertEquals("debmaster", corpoRecebido.get("payment_method_id").asText());
        assertEquals("optional", corpoRecebido.get("three_d_secure_mode").asText());
    }

    @Test
    void numeroDoCartaoNuncaPassaPeloSistemaSoOToken() {
        gateway().criarCobranca(pedido(MetodoPagamento.CARTAO_CREDITO, null, "tok_abc", 1, "visa"));

        String enviado = corpoRecebido.toString();
        assertFalse(enviado.contains("card_number"));
        assertFalse(enviado.contains("security_code"));
    }

    @Test
    void respostaDoPixTraduzCopiaEColaQrCodeELink() {
        corpoDaResposta = """
                {"id":555,"status":"pending","status_detail":"pending_waiting_transfer",
                 "date_of_expiration":"2026-12-10T23:59:59.000-03:00",
                 "point_of_interaction":{"transaction_data":{"qr_code":"00020126PIX","qr_code_base64":"iVBORw0KGgo=",
                 "ticket_url":"https://www.mercadopago.com.br/payments/555/ticket"}}}
                """;

        RespostaCobranca resposta = gateway().criarCobranca(pix());

        assertEquals("555", resposta.idTransacaoGateway());
        assertEquals(StatusPagamento.PENDENTE, resposta.status());
        assertEquals("pending_waiting_transfer", resposta.statusDetalhe());
        assertEquals("00020126PIX", resposta.pixCopiaECola());
        assertEquals("iVBORw0KGgo=", resposta.pixQrCodeBase64());
        assertEquals("https://www.mercadopago.com.br/payments/555/ticket", resposta.linkPagamento());
        assertNotNull(resposta.expiraEm());
    }

    @Test
    void respostaDoBoletoTraduzLinhaDigitavelELinkDoBoleto() {
        corpoDaResposta = """
                {"id":556,"status":"pending","status_detail":"pending_waiting_payment",
                 "barcode":{"content":"23793.38128 60000.000003 00000.000400 1 84340000180050"},
                 "transaction_details":{"external_resource_url":"https://www.mercadopago.com.br/sandbox/payments/556/ticket?pref_id=1"}}
                """;

        RespostaCobranca resposta = gateway().criarCobranca(
                pedido(MetodoPagamento.BOLETO, LocalDate.now().plusDays(5), null, null, null));

        assertEquals("23793.38128 60000.000003 00000.000400 1 84340000180050", resposta.linhaDigitavel());
        assertTrue(resposta.linkPagamento().contains("/payments/556/ticket"));
        assertNull(resposta.pixCopiaECola());
    }

    @Test
    void respostaDeCartaoCom3dsTrazOLinkDeAutenticacao() {
        corpoDaResposta = """
                {"id":557,"status":"pending","status_detail":"pending_challenge",
                 "three_ds_info":{"external_resource_url":"https://acs.banco.com/desafio","creq":"abc"}}
                """;

        RespostaCobranca resposta = gateway().criarCobranca(
                pedido(MetodoPagamento.CARTAO_DEBITO, null, "tok_abc", 1, "debmaster"));

        assertEquals("https://acs.banco.com/desafio", resposta.urlAutenticacao3ds());
    }

    private StatusPagamento statusPara(String statusDoMercadoPago) {
        corpoDaResposta = "{\"id\":1,\"status\":\"" + statusDoMercadoPago + "\"}";
        return gateway().consultarCobranca("1").status();
    }

    @Test
    void statusDoMercadoPagoViraStatusDoSistema() {
        assertEquals(StatusPagamento.PAGO, statusPara("approved"));
        assertEquals(StatusPagamento.PENDENTE, statusPara("pending"));
        assertEquals(StatusPagamento.PROCESSANDO, statusPara("in_process"));
        assertEquals(StatusPagamento.PROCESSANDO, statusPara("authorized"));
        assertEquals(StatusPagamento.PROCESSANDO, statusPara("in_mediation"));
        assertEquals(StatusPagamento.RECUSADO, statusPara("rejected"));
        assertEquals(StatusPagamento.RECUSADO, statusPara("cancelled"));
        assertEquals(StatusPagamento.ESTORNADO, statusPara("refunded"));
        assertEquals(StatusPagamento.ESTORNADO, statusPara("charged_back"));
    }

    @Test
    void statusDesconhecidoNuncaViraPagoPorEngano() {
        assertEquals(StatusPagamento.PENDENTE, statusPara("algo_novo_que_o_mp_inventou"));
        corpoDaResposta = "{\"id\":1}";
        assertEquals(StatusPagamento.PENDENTE, gateway().consultarCobranca("1").status());
    }

    @Test
    void consultarFazGetNoPagamentoCerto() {
        gateway().consultarCobranca("98765");

        assertEquals("GET", metodoRecebido);
        assertEquals("/v1/payments/98765", caminhoRecebido);
        assertEquals("Bearer APP_USR-token-de-teste", autorizacaoRecebida);
    }

    @Test
    void estornarFazPostEmRefundsComChaveDeIdempotencia() {
        gateway().estornarCobranca("98765");

        assertEquals("POST", metodoRecebido);
        assertEquals("/v1/payments/98765/refunds", caminhoRecebido);
        assertTrue(chaveDeIdempotenciaRecebida.startsWith("estorno-98765-"));
    }

    @Test
    void erroDaApiViraPaymentGatewayExceptionComOMotivoLegivel() {
        statusDaResposta = 400;
        corpoDaResposta = """
                {"message":"Invalid user identification number","error":"bad_request","status":400,
                 "cause":[{"code":2067,"description":"Invalid user identification number"}]}
                """;

        var erro = assertThrows(PaymentGatewayException.class, () -> gateway().criarCobranca(pix()));

        assertTrue(erro.getMessage().startsWith("Mercado Pago: "));
        assertTrue(erro.getMessage().contains("Invalid user identification number"));
    }

    @Test
    void erroSemCorpoEntendivelAindaViraPaymentGatewayException() {
        statusDaResposta = 502;
        corpoDaResposta = "<html>Bad gateway</html>";

        var erro = assertThrows(PaymentGatewayException.class, () -> gateway().consultarCobranca("1"));

        assertTrue(erro.getMessage().contains("502"));
    }

    @Test
    void servidorForaDoArViraPaymentGatewayExceptionSemVazarOToken() {
        MercadoPagoGateway gateway = gateway();
        servidor.stop(0);

        var erro = assertThrows(PaymentGatewayException.class, () -> gateway.criarCobranca(pix()));

        assertTrue(erro.getMessage().contains("comunicar"));
        assertFalse(erro.getMessage().contains("APP_USR-token-de-teste"));
    }
}
