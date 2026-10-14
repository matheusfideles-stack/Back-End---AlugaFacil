package com.larenegocios.alugafacil.infra.ratelimit;

import com.larenegocios.alugafacil.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 0,01 ficha/s: dentro de um teste nenhuma ficha volta, entao o total liberado e o burst. Cada teste usa um IP proprio.
@TestPropertySource(properties = {
        "ratelimit.autenticacao.requests-per-second=0.01",
        "ratelimit.autenticacao.burst=3",
        "ratelimit.pagamentos.requests-per-second=0.01",
        "ratelimit.pagamentos.burst=2",
        "ratelimit.webhooks.requests-per-second=0.01",
        "ratelimit.webhooks.burst=2",
        "ratelimit.geral.requests-per-second=0.01",
        "ratelimit.geral.burst=3"
})
class RateLimitIntegrationTest extends IntegracaoBase {

    private MockHttpServletRequestBuilder de(String ip, MockHttpServletRequestBuilder requisicao) {
        return requisicao.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private Map<String, String> credenciaisInvalidas() {
        return Map.of("email", "ninguem@teste.com", "senha", "senha-errada");
    }

    @Test
    void loginEBloqueadoDepoisDoBurstComRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(de("10.0.1.1", postJson("/auth/login", credenciaisInvalidas())))
                    .andExpect(status().isUnauthorized());
        }

        String retryAfter = mockMvc.perform(de("10.0.1.1", postJson("/auth/login", credenciaisInvalidas())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Muitas requisicoes")))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        long segundos = Long.parseLong(retryAfter);
        assertTrue(segundos >= 1 && segundos <= 100, "Retry-After fora do esperado: " + segundos);
    }

    @Test
    void cadastroECompartilhaOLimiteDeLoginPorqueAmbosSaoRotasDeSenha() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(de("10.0.2.1", postJson("/auth/registro", Map.of("nome", ""))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(de("10.0.2.1", postJson("/auth/login", credenciaisInvalidas())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cobrancaDePixEOutrasRotasDePagamentoSaoLimitadasAntesDaAutenticacao() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(de("10.0.3.1", postJson("/pagamentos", Map.of("metodo", "PIX"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(de("10.0.3.1", postJson("/pagamentos", Map.of("metodo", "PIX"))))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(de("10.0.3.1", postJson("/pagamentos/1/estornar", Map.of())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void webhookTemLimiteProprio() throws Exception {
        Map<String, Object> notificacao = Map.of("type", "outro");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(de("10.0.4.1", postJson("/webhooks/mercadopago", notificacao)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(de("10.0.4.1", postJson("/webhooks/mercadopago", notificacao)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void restanteDaApiTambemELimitadoMasSemAfetarAsOutrasCategorias() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(de("10.0.5.1", get("/imoveis"))).andExpect(status().isOk());
        }
        mockMvc.perform(de("10.0.5.1", get("/imoveis"))).andExpect(status().isTooManyRequests());

        mockMvc.perform(de("10.0.5.1", postJson("/auth/login", credenciaisInvalidas())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void umIpBloqueadoNaoAfetaOutroIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(de("10.0.6.1", get("/imoveis")));
        }
        mockMvc.perform(de("10.0.6.1", get("/imoveis"))).andExpect(status().isTooManyRequests());

        mockMvc.perform(de("10.0.6.2", get("/imoveis"))).andExpect(status().isOk());
    }
}
