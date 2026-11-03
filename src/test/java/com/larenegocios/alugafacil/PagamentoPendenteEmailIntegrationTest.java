package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGateway;
import com.larenegocios.alugafacil.domain.pagamento.gateway.RespostaCobranca;
import com.larenegocios.alugafacil.domain.pagamento.gateway.SolicitacaoCobranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Gateway que cria a cobranca como pendente e so confirma quando e consultado: imita Pix e boleto de verdade.
@Import({RecuperacaoSenhaIntegrationTest.EmailsCapturados.class, PagamentoPendenteEmailIntegrationTest.GatewayQueConfirmaDepois.class})
class PagamentoPendenteEmailIntegrationTest extends IntegracaoBase {

    @TestConfiguration
    static class GatewayQueConfirmaDepois {

        @Bean
        @Primary
        PaymentGateway gatewayQueConfirmaDepois() {
            return new PaymentGateway() {
                @Override
                public RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao) {
                    return new RespostaCobranca("PEND-" + UUID.randomUUID(), StatusPagamento.PENDENTE, "pending_waiting_transfer",
                            null, "00020126PENDENTE", null, null, null, LocalDateTime.now().plusDays(1));
                }

                @Override
                public RespostaCobranca consultarCobranca(String idTransacaoGateway) {
                    return new RespostaCobranca(idTransacaoGateway, StatusPagamento.PAGO, "accredited",
                            null, null, null, null, null, null);
                }

                @Override
                public void estornarCobranca(String idTransacaoGateway) {
                }
            };
        }
    }

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    private String tokenLocatario;
    private long contratoId;

    @BeforeEach
    void criarContrato() throws Exception {
        String tokenLocador = tokenDe("LOCADOR");
        tokenLocatario = tokenDe("LOCATARIO");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = contratar(imovelId, tokenLocatario, tokenLocador);
        caixa.enviados.clear();
    }

    private long gerarPixPendente(String mes) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", mes);
        dados.put("metodo", "PIX");
        return idDaResposta(mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE")));
    }

    private void sincronizar(long pagamentoId) throws Exception {
        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/sincronizar").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk());
    }

    @Test
    void cobrancaPendenteNaoAvisaNinguem() throws Exception {
        gerarPixPendente("2026-12");

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void confirmacaoDepoisDoPedidoAvisaOsDois() throws Exception {
        long id = gerarPixPendente("2026-12");

        sincronizar(id);

        assertEquals(2, caixa.enviados.size());
        assertTrue(caixa.enviados.stream().anyMatch(e -> e.assunto().contains("pagamento confirmado")));
        assertTrue(caixa.enviados.stream().anyMatch(e -> e.assunto().contains("pagamento recebido")));
    }

    @Test
    void confirmarDeNovoNaoRepeteOsEmails() throws Exception {
        long id = gerarPixPendente("2026-12");

        sincronizar(id);
        sincronizar(id);
        sincronizar(id);

        assertEquals(2, caixa.enviados.size());
    }

    @Test
    void cadaPagamentoConfirmadoGeraOsSeusProprios() throws Exception {
        long primeiro = gerarPixPendente("2026-12");
        long segundo = gerarPixPendente("2027-01");

        sincronizar(primeiro);
        sincronizar(segundo);

        assertEquals(4, caixa.enviados.size());
    }
}
