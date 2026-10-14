package com.larenegocios.alugafacil.infra.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private final AtomicLong agora = new AtomicLong(0);
    private RateLimitProperties propriedades;
    private RateLimiter limiter;

    @BeforeEach
    void preparar() {
        propriedades = new RateLimitProperties();
        propriedades.setAutenticacao(new RateLimitProperties.Regra(1, 2));
        propriedades.setPagamentos(new RateLimitProperties.Regra(1, 1));
        propriedades.setGeral(new RateLimitProperties.Regra(5, 5));
        limiter = new RateLimiter(propriedades, agora::get);
    }

    @ParameterizedTest
    @CsvSource({
            "/auth/login, AUTENTICACAO",
            "/auth/registro, AUTENTICACAO",
            "/auth, AUTENTICACAO",
            "/pagamentos, PAGAMENTOS",
            "/pagamentos/10/estornar, PAGAMENTOS",
            "/webhooks/mercadopago, WEBHOOKS",
            "/imoveis, GERAL",
            "/contratos/meus, GERAL",
            "/authentication, GERAL",
            "/pagamentosfake, GERAL"
    })
    void classificaOCaminhoNaCategoriaCerta(String caminho, CategoriaRateLimit esperada) {
        assertEquals(esperada, CategoriaRateLimit.de(caminho));
    }

    @Test
    void bloqueiaDepoisDoBurstEInformaRetryAfter() {
        assertTrue(limiter.tentar(CategoriaRateLimit.AUTENTICACAO, "1.1.1.1").permitida());
        assertTrue(limiter.tentar(CategoriaRateLimit.AUTENTICACAO, "1.1.1.1").permitida());

        RateLimiter.Decisao bloqueada = limiter.tentar(CategoriaRateLimit.AUTENTICACAO, "1.1.1.1");

        assertFalse(bloqueada.permitida());
        assertEquals(1, bloqueada.segundosParaTentarNovamente());
    }

    @Test
    void liberaDeNovoQuandoOTempoPassa() {
        limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1");
        assertFalse(limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1").permitida());

        agora.addAndGet(TimeUnit.SECONDS.toNanos(1));

        assertTrue(limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1").permitida());
    }

    @Test
    void cadaIpTemOProprioLimite() {
        limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1");
        assertFalse(limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1").permitida());

        assertTrue(limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "2.2.2.2").permitida());
    }

    @Test
    void cadaCategoriaTemOProprioLimite() {
        limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1");
        assertFalse(limiter.tentar(CategoriaRateLimit.PAGAMENTOS, "1.1.1.1").permitida());

        assertTrue(limiter.tentar(CategoriaRateLimit.GERAL, "1.1.1.1").permitida());
    }
}
