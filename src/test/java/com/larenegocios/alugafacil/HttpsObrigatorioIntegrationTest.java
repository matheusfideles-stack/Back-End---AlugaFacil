package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.https-obrigatorio=true")
class HttpsObrigatorioIntegrationTest extends IntegracaoBase {

    @Test
    void acessoPorHttpEhRedirecionadoParaHttps() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://")));
    }

    @Test
    void apiPorHttpTambemEhRedirecionada() throws Exception {
        mockMvc.perform(get("/imoveis"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://")));
    }

    @Test
    void acessoPorHttpsFuncionaEEnviaHsts() throws Exception {
        mockMvc.perform(get("/index.html").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().exists("Strict-Transport-Security"));
    }
}
