package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
@TestPropertySource(properties = "app.exibir-links-de-teste=true")
class RecuperacaoSenhaModoTesteIntegrationTest extends IntegracaoBase {

    @Test
    void semEmailConfiguradoEComModoTesteLigadoARespostaTrazOCodigo() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");

        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", usuario.get("email"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoDeTeste", matchesPattern("\\d{6}")));
    }

    @Test
    void emailInexistenteNuncaRecebeCodigoMesmoNoModoTeste() throws Exception {
        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", "ninguem@teste.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoDeTeste").doesNotExist());
    }
}
