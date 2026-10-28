package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class ContatoIntegrationTest extends IntegracaoBase {

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    @BeforeEach
    void limparCaixa() {
        caixa.enviados.clear();
    }

    private Map<String, Object> mensagem() {
        return Map.of("nome", "Maria", "email", "maria@teste.com", "assunto", "Duvida sobre pagamento",
                "mensagem", "Nao consegui gerar o boleto deste mes.");
    }

    @Test
    void visitanteEnviaMensagemSemLogin() throws Exception {
        mockMvc.perform(postJson("/contato", mensagem()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        assertEquals(1, caixa.enviados.size());
        var enviado = caixa.enviados.get(0);
        assertEquals("suporte@alugafacil.local", enviado.para());
        assertTrue(enviado.assunto().contains("Duvida sobre pagamento"));
        assertTrue(enviado.texto().contains("maria@teste.com"));
        assertTrue(enviado.texto().contains("Nao consegui gerar o boleto"));
    }

    @Test
    void mensagemInvalidaDevolve400SemEnviarNada() throws Exception {
        mockMvc.perform(postJson("/contato",
                        Map.of("nome", "", "email", "invalido", "assunto", "x", "mensagem", "curta")))
                .andExpect(status().isBadRequest());

        assertTrue(caixa.enviados.isEmpty());
    }
}
