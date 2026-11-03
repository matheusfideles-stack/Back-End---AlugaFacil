package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.pagamento.LembretePagamentoRepository;
import com.larenegocios.alugafacil.service.LembreteDePagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Aplicacao inteira, banco de verdade (H2) e contrato criado pela API. Outros testes deixam contratos no banco, entao
// aqui so se olha para os e-mails destas duas pessoas.
@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class LembreteDePagamentoIntegrationTest extends IntegracaoBase {

    @Autowired
    private LembreteDePagamentoService lembretes;

    @Autowired
    private LembretePagamentoRepository lembreteRepository;

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    private Map<String, Object> locador;
    private Map<String, Object> locatario;
    private LocalDate vencimento;

    @BeforeEach
    void criarContrato() throws Exception {
        locador = cadastrar("LOCADOR");
        locatario = cadastrar("LOCATARIO");
        String tokenLocatario = login(locatario);
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), login(locador))));

        // Contrato que comeca no mes que vem: o unico vencimento em jogo e o do dia 10 daquele mes.
        YearMonth mesQueVem = YearMonth.now().plusMonths(1);
        vencimento = mesQueVem.atDay(10);
        Map<String, Object> dados = new LinkedHashMap<>(dadosContrato(imovelId));
        dados.put("dataInicio", mesQueVem.atDay(1).toString());
        dados.put("dataFim", mesQueVem.plusMonths(12).atDay(1).toString());
        dados.put("diaVencimento", 10);
        contratar(dados, tokenLocatario, login(locador));
        caixa.enviados.clear();
    }

    private List<RecuperacaoSenhaIntegrationTest.EmailEnviado> emailsPara(Object endereco) {
        return caixa.enviados.stream().filter(e -> e.para().equals(endereco)).toList();
    }

    @Test
    void nosTestesARotinaDiariaFicaDesligadaParaNaoMandarEmailSozinha(
            @Autowired org.springframework.context.ApplicationContext contexto) {
        assertEquals(0, contexto.getBeanNamesForType(com.larenegocios.alugafacil.service.AgendadorDeLembretes.class).length);
    }

    @Test
    void lembreteSaiTresDiasAntesComOsDadosDoContratoReal() {
        lembretes.enviarLembretes(vencimento.minusDays(3));

        var emails = emailsPara(locatario.get("email"));
        assertEquals(1, emails.size());
        assertTrue(emails.get(0).assunto().contains("esta para vencer"));
        assertTrue(emails.get(0).texto().contains("R$ 1.800,00"));
        assertTrue(emails.get(0).texto().contains((String) locatario.get("nome")));
        assertTrue(emailsPara(locador.get("email")).isEmpty());
    }

    @Test
    void rodarDeNovoNaoRepeteOAvisoPorqueOBancoGuardaORegistro() {
        lembretes.enviarLembretes(vencimento.minusDays(3));
        long registrosDepoisDoPrimeiro = lembreteRepository.count();
        lembretes.enviarLembretes(vencimento.minusDays(3));
        lembretes.enviarLembretes(vencimento.minusDays(2));

        assertEquals(1, emailsPara(locatario.get("email")).size());
        assertEquals(registrosDepoisDoPrimeiro, lembreteRepository.count());
    }

    @Test
    void atrasoDeTresDiasAvisaOLocatarioEOLocador() {
        lembretes.enviarLembretes(vencimento.plusDays(3));

        assertEquals(1, emailsPara(locatario.get("email")).size());
        var paraOLocador = emailsPara(locador.get("email"));
        assertEquals(1, paraOLocador.size());
        assertTrue(paraOLocador.get(0).texto().contains("3 dia(s) de atraso"));
    }

    @Test
    void depoisDePagarOLembreteNaoSai() throws Exception {
        String tokenLocatario = login(locatario);
        var contratos = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/contratos/meus?papel=locatario")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andReturn().getResponse().getContentAsString();
        long contratoId = ((Number) com.jayway.jsonpath.JsonPath.read(contratos, "$.content[0].id")).longValue();

        Map<String, Object> pagamento = new LinkedHashMap<>();
        pagamento.put("contratoId", contratoId);
        pagamento.put("referenciaMes", YearMonth.from(vencimento).toString());
        pagamento.put("metodo", "PIX");
        mockMvc.perform(postJson("/pagamentos", pagamento, tokenLocatario)).andExpect(status().isOk());
        caixa.enviados.clear();

        lembretes.enviarLembretes(vencimento.plusDays(1));

        assertTrue(emailsPara(locatario.get("email")).isEmpty());
    }
}
