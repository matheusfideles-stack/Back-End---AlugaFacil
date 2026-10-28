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
class ConfirmacaoEmailModoTesteIntegrationTest extends IntegracaoBase {

    @Test
    void semEmailConfiguradoOCadastroDevolveOCodigoDeVerificacaoNaResposta() throws Exception {
        mockMvc.perform(postJson("/auth/registro", dadosUsuario("LOCATARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmado").value(false))
                .andExpect(jsonPath("$.codigoDeTeste", matchesPattern("\\d{6}")));
    }

    @Test
    void reenvioTambemDevolveOCodigoNoModoTeste() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();

        mockMvc.perform(postJson("/auth/reenviar-confirmacao", Map.of("email", usuario.get("email"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoDeTeste", matchesPattern("\\d{6}")));
    }
}
