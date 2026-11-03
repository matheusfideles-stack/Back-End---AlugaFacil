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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Avisos ao proprietario (avaliacao, manutencao, pagamento recusado) e ao locatario (manutencao). O gateway deste teste
// recusa toda cobranca, para provocar o aviso de pagamento recusado.
@Import({RecuperacaoSenhaIntegrationTest.EmailsCapturados.class, NotificacoesIntegrationTest.GatewayQueRecusa.class})
class NotificacoesIntegrationTest extends IntegracaoBase {

    @TestConfiguration
    static class GatewayQueRecusa {

        @Bean
        @Primary
        PaymentGateway gatewayQueRecusa() {
            return new PaymentGateway() {
                @Override
                public RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao) {
                    return new RespostaCobranca("REC-" + UUID.randomUUID(), StatusPagamento.RECUSADO, "cc_rejected_other_reason",
                            null, null, null, null, null, null);
                }

                @Override
                public RespostaCobranca consultarCobranca(String idTransacaoGateway) {
                    return new RespostaCobranca(idTransacaoGateway, StatusPagamento.RECUSADO, "cc_rejected_other_reason",
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

    private Map<String, Object> locador;
    private Map<String, Object> locatario;
    private String tokenLocador;
    private String tokenLocatario;
    private long imovelId;
    private long contratoId;

    @BeforeEach
    void preparar() throws Exception {
        locador = cadastrar("LOCADOR");
        locatario = cadastrar("LOCATARIO");
        tokenLocador = login(locador);
        tokenLocatario = login(locatario);
        imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = contratar(imovelId, tokenLocatario, tokenLocador);
        caixa.enviados.clear();
    }

    private List<RecuperacaoSenhaIntegrationTest.EmailEnviado> emailsPara(Object endereco) {
        return caixa.enviados.stream().filter(e -> e.para().equals(endereco)).toList();
    }

    private void avaliar(int nota, String comentario) throws Exception {
        mockMvc.perform(put("/imoveis/" + imovelId + "/avaliacoes/minha").header("Authorization", "Bearer " + tokenLocatario)
                        .contentType("application/json").content(json(Map.of("nota", nota, "comentario", comentario))))
                .andExpect(status().isOk());
    }

    private long solicitarManutencao() throws Exception {
        return idDaResposta(mockMvc.perform(postJson("/manutencoes",
                Map.of("contratoId", contratoId, "tipo", "Eletrica", "urgencia", "URGENTE",
                        "descricao", "Tomada da sala sem energia"), tokenLocatario)).andExpect(status().isOk()));
    }

    private void mudarStatus(long id, String novoStatus) throws Exception {
        mockMvc.perform(put("/manutencoes/" + id + "/status").header("Authorization", "Bearer " + tokenLocador)
                        .contentType("application/json").content(json(Map.of("status", novoStatus))))
                .andExpect(status().isOk());
    }

    // ----- avaliacao

    @Test
    void novaAvaliacaoAvisaOProprietarioComNotaEComentario() throws Exception {
        avaliar(4, "Otimo lugar para morar");

        var email = emailsPara(locador.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("nova avaliacao"));
        assertTrue(email.get(0).texto().contains("nota 4 de 5"));
        assertTrue(email.get(0).texto().contains("Otimo lugar para morar"));
        assertTrue(email.get(0).texto().contains((String) locatario.get("nome")));
    }

    @Test
    void editarAPropriaAvaliacaoNaoMandaOutroEmail() throws Exception {
        avaliar(4, "Bom");
        avaliar(2, "Mudei de ideia");

        assertEquals(1, emailsPara(locador.get("email")).size());
    }

    // ----- manutencao

    @Test
    void pedidoDeManutencaoAvisaOProprietario() throws Exception {
        solicitarManutencao();

        var email = emailsPara(locador.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("manutencao"));
        assertTrue(email.get(0).texto().contains("Tomada da sala sem energia"));
        assertTrue(email.get(0).texto().contains("URGENTE"));
    }

    @Test
    void mudarOStatusDaManutencaoAvisaOLocatario() throws Exception {
        long id = solicitarManutencao();
        caixa.enviados.clear();

        mudarStatus(id, "EM_ANDAMENTO");

        var email = emailsPara(locatario.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("em andamento"));
        assertTrue(email.get(0).texto().contains("/alugueis.html"));
    }

    @Test
    void repetirOMesmoStatusNaoMandaOutroEmail() throws Exception {
        long id = solicitarManutencao();
        mudarStatus(id, "EM_ANDAMENTO");
        caixa.enviados.clear();

        mudarStatus(id, "EM_ANDAMENTO");

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void concluirAManutencaoAvisaQueFoiConcluida() throws Exception {
        long id = solicitarManutencao();
        caixa.enviados.clear();

        mudarStatus(id, "CONCLUIDA");

        assertTrue(emailsPara(locatario.get("email")).get(0).assunto().contains("concluida"));
    }

    // ----- pagamento recusado

    @Test
    void pagamentoRecusadoAvisaOProprietarioComOMotivoEOValor() throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", "2026-12");
        dados.put("metodo", "PIX");

        mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario));

        var email = emailsPara(locador.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("pagamento recusado"));
        assertTrue(email.get(0).texto().contains("12/2026"));
        assertTrue(email.get(0).texto().contains("cc_rejected_other_reason"));
        assertTrue(email.get(0).texto().contains("R$ 1.800,00"));
        assertTrue(email.get(0).texto().contains("/financeiro.html"));
    }

    @Test
    void sincronizarUmPagamentoJaRecusadoNaoMandaOutroAviso() throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", "2026-12");
        dados.put("metodo", "PIX");
        long pagamentoId = idDaResposta(mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario)));
        caixa.enviados.clear();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/pagamentos/" + pagamentoId + "/sincronizar").header("Authorization", "Bearer " + tokenLocatario));

        assertTrue(caixa.enviados.isEmpty());
    }
}
