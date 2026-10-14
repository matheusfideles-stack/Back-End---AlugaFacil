package com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoWebhookValidatorTest {

    private static final String SEGREDO = "segredo-do-webhook";
    private static final String DATA_ID = "123456789";
    private static final String REQUEST_ID = "req-abc";
    private static final String TS = "1700000000";

    private MercadoPagoProperties propriedades;
    private MercadoPagoWebhookValidator validator;

    @BeforeEach
    void preparar() {
        propriedades = new MercadoPagoProperties();
        propriedades.setWebhookSecret(SEGREDO);
        validator = new MercadoPagoWebhookValidator(propriedades);
    }

    private String assinar(String manifesto, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    private String cabecalho(String v1) {
        return "ts=" + TS + ",v1=" + v1;
    }

    @Test
    void aceitaAssinaturaCorreta() throws Exception {
        String v1 = assinar("id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + TS + ";", SEGREDO);

        assertTrue(validator.assinaturaValida(cabecalho(v1), REQUEST_ID, DATA_ID));
    }

    @Test
    void aceitaAssinaturaSemRequestId() throws Exception {
        String v1 = assinar("id:" + DATA_ID + ";ts:" + TS + ";", SEGREDO);

        assertTrue(validator.assinaturaValida(cabecalho(v1), null, DATA_ID));
    }

    @Test
    void rejeitaAssinaturaFeitaComOutroSegredo() throws Exception {
        String v1 = assinar("id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + TS + ";", "outro-segredo");

        assertFalse(validator.assinaturaValida(cabecalho(v1), REQUEST_ID, DATA_ID));
    }

    @Test
    void rejeitaQuandoOIdDaTransacaoFoiAdulterado() throws Exception {
        String v1 = assinar("id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + TS + ";", SEGREDO);

        assertFalse(validator.assinaturaValida(cabecalho(v1), REQUEST_ID, "999"));
    }

    @Test
    void rejeitaCabecalhoAusenteOuMalFormado() {
        assertFalse(validator.assinaturaValida(null, REQUEST_ID, DATA_ID));
        assertFalse(validator.assinaturaValida("", REQUEST_ID, DATA_ID));
        assertFalse(validator.assinaturaValida("lixo", REQUEST_ID, DATA_ID));
        assertFalse(validator.assinaturaValida("ts=" + TS, REQUEST_ID, DATA_ID));
        assertFalse(validator.assinaturaValida("v1=abc", REQUEST_ID, DATA_ID));
    }

    @Test
    void rejeitaQuandoNaoHaIdDaTransacao() throws Exception {
        String v1 = assinar("id:;ts:" + TS + ";", SEGREDO);

        assertFalse(validator.assinaturaValida(cabecalho(v1), REQUEST_ID, ""));
    }

    @Test
    void semSegredoConfiguradoAValidacaoEIgnorada() {
        propriedades.setWebhookSecret("");

        assertTrue(validator.assinaturaValida(null, null, DATA_ID));
    }
}
