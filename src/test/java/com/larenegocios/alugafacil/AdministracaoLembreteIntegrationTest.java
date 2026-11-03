package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O gateway deste teste deixa o pagamento pendente ate ser consultado: so assim da para testar o lembrete.
@Import({RecuperacaoSenhaIntegrationTest.EmailsCapturados.class,
        PagamentoPendenteEmailIntegrationTest.GatewayQueConfirmaDepois.class})
class AdministracaoLembreteIntegrationTest extends IntegracaoBase {

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    private String tokenAdmin;
    private String tokenLocatario;
    private String emailLocatario;
    private long contratoId;

    @BeforeEach
    void criarContrato() throws Exception {
        tokenAdmin = tokenDe("ADMIN");
        String tokenLocador = tokenDe("LOCADOR");
        Map<String, Object> locatario = cadastrar("LOCATARIO");
        tokenLocatario = login(locatario);
        emailLocatario = (String) locatario.get("email");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = idDaResposta(mockMvc.perform(postJson("/contratos", dadosContrato(imovelId), tokenLocatario)));
        caixa.enviados.clear();
    }

    private long gerarPixPendente(String mes) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", mes);
        dados.put("metodo", "PIX");
        return idDaResposta(mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario)));
    }

    private List<RecuperacaoSenhaIntegrationTest.EmailEnviado> emailsPara(String endereco) {
        return caixa.enviados.stream().filter(e -> e.para().equals(endereco)).toList();
    }

    @Test
    void adminEnviaLembreteDoPagamentoPendenteComPrazoDeCincoDias() throws Exception {
        long pagamentoId = gerarPixPendente("2026-12");

        mockMvc.perform(post("/admin/pagamentos/" + pagamentoId + "/lembrete")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        var emails = emailsPara(emailLocatario);
        assertEquals(1, emails.size());
        var email = emails.get(0);
        assertTrue(email.assunto().contains("lembrete"));
        assertTrue(email.assunto().contains("12/2026"));
        assertTrue(email.texto().contains("R$ 1.800,00"));
        assertTrue(email.texto().contains("Pague ate"));
        assertTrue(email.texto().contains("em ate 5 dias"));
        assertTrue(email.texto().contains("/pagamentos.html"));
    }

    @Test
    void lembreteNaoSaiParaPagamentoJaConfirmado() throws Exception {
        long pagamentoId = gerarPixPendente("2026-12");
        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/sincronizar")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk());
        caixa.enviados.clear();

        mockMvc.perform(post("/admin/pagamentos/" + pagamentoId + "/lembrete")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isUnprocessableEntity());

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void somenteOAdminEnviaLembrete() throws Exception {
        long pagamentoId = gerarPixPendente("2026-12");

        mockMvc.perform(post("/admin/pagamentos/" + pagamentoId + "/lembrete")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isForbidden());

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void pagamentoInexistenteDaNaoEncontrado() throws Exception {
        mockMvc.perform(post("/admin/pagamentos/999999/lembrete")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }
}
