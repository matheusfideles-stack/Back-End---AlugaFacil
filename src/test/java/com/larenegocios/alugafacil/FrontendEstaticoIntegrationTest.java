package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "ratelimit.geral.requests-per-second=0.01",
        "ratelimit.geral.burst=2"
})
class FrontendEstaticoIntegrationTest extends IntegracaoBase {

    @Test
    void paginasPublicasSaoServidasSemLogin() throws Exception {
        for (String pagina : new String[]{"/index.html", "/login.html", "/esqueci-senha.html",
                "/redefinir-senha.html", "/ajuda.html", "/sair.html", "/imoveis.html"}) {
            mockMvc.perform(get(pagina)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    void cssEJavascriptSaoServidos() throws Exception {
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/api.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("const Sessao")));
    }

    @Test
    void paginaInicialNaRaizResponde() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void arquivosEstaticosNaoConsomemOLimiteDeRequisicoes() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
            mockMvc.perform(get("/login.html")).andExpect(status().isOk());
        }
    }

    @Test
    void apiContinuaLimitadaMesmoComOFrontendNoMesmoServidor() throws Exception {
        mockMvc.perform(get("/imoveis")).andExpect(status().isOk());
        mockMvc.perform(get("/imoveis")).andExpect(status().isOk());
        mockMvc.perform(get("/imoveis")).andExpect(status().isTooManyRequests());
    }

    @Test
    void menuImoveisAbreATelaDeImoveisEmVezDaInicial() throws Exception {
        mockMvc.perform(get("/js/api.js"))
                .andExpect(content().string(containsString("'imoveis.html'")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("index.html#vitrine"))));
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(containsString("action=\"imoveis.html\"")));
    }

    @Test
    void paginasDoFrontendNaoApontamParaDadosFicticios() throws Exception {
        for (String pagina : new String[]{"/login.html", "/index.html", "/pagamentos.html", "/cliente.html"}) {
            mockMvc.perform(get(pagina))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("fictício"))));
        }
        mockMvc.perform(get("/js/api.js"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("iniciarSessaoLocal"))));
    }
}
