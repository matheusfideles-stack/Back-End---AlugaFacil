package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.infra.security.VerificacaoDeSegurancaNaSubida;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificacaoDeSegurancaNaSubidaTest extends IntegracaoBase {

    private static final String SEGREDO_BOM = "x7Kp2vQmL9sRtW4zYb8NcD1fGh5JaE3u";
    private static final String SERVIDOR_DE_EMAIL = "smtp.exemplo.com";

    private static final String URL_DO_WEBHOOK = "https://exemplo.com/webhooks/mercadopago";

    private VerificacaoDeSegurancaNaSubida verificacao(String segredo, String senhaDoAdministrador, String servidorDeEmail) {
        return verificacaoDePagamento("mercadopago", "APP_USR-token", "segredo-do-webhook", URL_DO_WEBHOOK,
                segredo, senhaDoAdministrador, servidorDeEmail);
    }

    private VerificacaoDeSegurancaNaSubida verificacaoDePagamento(String provider, String accessToken,
                                                                  String segredoDoWebhook, String urlDeNotificacao) {
        return verificacaoDePagamento(provider, accessToken, segredoDoWebhook, urlDeNotificacao,
                SEGREDO_BOM, "", SERVIDOR_DE_EMAIL);
    }

    private VerificacaoDeSegurancaNaSubida verificacaoDePagamento(String provider, String accessToken,
                                                                  String segredoDoWebhook, String urlDeNotificacao,
                                                                  String segredo, String senhaDoAdministrador,
                                                                  String servidorDeEmail) {
        return new VerificacaoDeSegurancaNaSubida(usuarioRepository, passwordEncoder, segredo, senhaDoAdministrador,
                servidorDeEmail, provider, accessToken, segredoDoWebhook, urlDeNotificacao);
    }

    private String erroDePagamento(String provider, String accessToken, String segredoDoWebhook, String urlDeNotificacao) {
        return assertThrows(IllegalStateException.class,
                () -> verificacaoDePagamento(provider, accessToken, segredoDoWebhook, urlDeNotificacao).run(null))
                .getMessage();
    }

    @Test
    void gatewayFakeImpedeASubidaEmProducao() {
        assertTrue(erroDePagamento("fake", "", "", "").contains("PAGAMENTO_PROVIDER"));
        assertTrue(erroDePagamento("", "APP_USR-token", "segredo", URL_DO_WEBHOOK).contains("PAGAMENTO_PROVIDER"));
        assertTrue(erroDePagamento(null, "APP_USR-token", "segredo", URL_DO_WEBHOOK).contains("PAGAMENTO_PROVIDER"));
    }

    @Test
    void mercadoPagoSemTokenImpedeASubida() {
        assertTrue(erroDePagamento("mercadopago", " ", "segredo", URL_DO_WEBHOOK).contains("MP_ACCESS_TOKEN"));
    }

    @Test
    void mercadoPagoSemSegredoDoWebhookImpedeASubida() {
        assertTrue(erroDePagamento("mercadopago", "APP_USR-token", "", URL_DO_WEBHOOK).contains("MP_WEBHOOK_SECRET"));
    }

    @Test
    void urlDoWebhookPrecisaSerHttps() {
        assertTrue(erroDePagamento("mercadopago", "APP_USR-token", "segredo", "").contains("MP_NOTIFICATION_URL"));
        assertTrue(erroDePagamento("mercadopago", "APP_USR-token", "segredo", "http://exemplo.com/webhooks/mercadopago")
                .contains("MP_NOTIFICATION_URL"));
    }

    @Test
    void mensagemDePagamentoNaoRevelaOTokenNemOSegredoDoWebhook() {
        String mensagem = erroDePagamento("mercadopago", "APP_USR-token-secreto", "", "");

        assertTrue(!mensagem.contains("APP_USR-token-secreto"));
    }

    @Test
    void pagamentoCompletoNaoGeraProblemaDePagamento() {
        try {
            verificacaoDePagamento("MercadoPago", "APP_USR-token", "segredo", URL_DO_WEBHOOK).run(null);
        } catch (IllegalStateException erro) {
            assertTrue(!erro.getMessage().contains("PAGAMENTO_PROVIDER"));
            assertTrue(!erro.getMessage().contains("MP_"));
        }
    }

    @Test
    void segredoDeExemploImpedeASubida() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> verificacao("alugafacil-secret-trocar-em-producao", "", SERVIDOR_DE_EMAIL).run(null));

        assertTrue(erro.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void senhaDeAdministradorCurtaImpedeASubida() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> verificacao(SEGREDO_BOM, "curta", SERVIDOR_DE_EMAIL).run(null));

        assertTrue(erro.getMessage().contains("ADMIN_SENHA"));
    }

    @Test
    void semServidorDeEmailImpedeASubida() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> verificacao(SEGREDO_BOM, "", "").run(null));

        assertTrue(erro.getMessage().contains("SPRING_MAIL_HOST"));
        assertTrue(erro.getMessage().contains("docs/EMAIL.md"));
    }

    @Test
    void servidorDeEmailEmBrancoOuAusenteImpedeASubida() {
        assertThrows(IllegalStateException.class, () -> verificacao(SEGREDO_BOM, "", "   ").run(null));
        assertThrows(IllegalStateException.class, () -> verificacao(SEGREDO_BOM, "", null).run(null));
    }

    @Test
    void administradorJaExistenteComSenhaConhecidaImpedeASubida() {
        Object email = cadastrarAdmin().get("email");

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> verificacao(SEGREDO_BOM, "", SERVIDOR_DE_EMAIL).run(null));

        assertTrue(erro.getMessage().contains(String.valueOf(email)));
        assertTrue(erro.getMessage().contains("docs/PRODUCAO.md"));
    }

    @Test
    void mensagemNaoRevelaOSegredoInformado() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> verificacao("segredo-curto-demais", "", SERVIDOR_DE_EMAIL).run(null));

        assertTrue(!erro.getMessage().contains("segredo-curto-demais"));
    }

    @Test
    void todosOsProblemasAparecemJuntos() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> verificacao("curto", "123", "").run(null));

        assertTrue(erro.getMessage().contains("JWT_SECRET"));
        assertTrue(erro.getMessage().contains("ADMIN_SENHA"));
        assertTrue(erro.getMessage().contains("SPRING_MAIL_HOST"));
    }

    @Test
    void configuracaoSemProblemasNaoLancaErroDasRegrasDeConfiguracao() {
        // Pode haver administradores com senha conhecida criados por outros testes; aqui so conferimos que o
        // erro, se existir, e apenas sobre eles e nunca sobre segredo, senha nova ou e-mail.
        try {
            verificacao(SEGREDO_BOM, "UmaSenhaLonga9!", SERVIDOR_DE_EMAIL).run(null);
        } catch (IllegalStateException erro) {
            assertTrue(!erro.getMessage().contains("JWT_SECRET"));
            assertTrue(!erro.getMessage().contains("ADMIN_SENHA"));
            assertTrue(!erro.getMessage().contains("SPRING_MAIL_HOST"));
        }
        assertDoesNotThrow(() -> verificacao(SEGREDO_BOM, "UmaSenhaLonga9!", SERVIDOR_DE_EMAIL));
    }
}
