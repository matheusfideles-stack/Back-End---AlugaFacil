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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O gateway de teste aprova na hora: aqui o aviso sai no proprio pedido de pagamento.
@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class PagamentoConfirmadoEmailIntegrationTest extends IntegracaoBase {

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    private Map<String, Object> locador;
    private Map<String, Object> locatario;
    private String tokenLocatario;
    private long contratoId;

    @BeforeEach
    void criarContrato() throws Exception {
        locador = cadastrar("LOCADOR");
        locatario = cadastrar("LOCATARIO");
        tokenLocatario = login(locatario);

        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), login(locador))));
        contratoId = idDaResposta(mockMvc.perform(postJson("/contratos", dadosContrato(imovelId), tokenLocatario)));
        caixa.enviados.clear();
    }

    private Map<String, Object> cobranca(String metodo, String referenciaMes) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", referenciaMes);
        dados.put("metodo", metodo);
        return dados;
    }

    private List<RecuperacaoSenhaIntegrationTest.EmailEnviado> emailsPara(Object endereco) {
        return caixa.enviados.stream().filter(e -> e.para().equals(endereco)).toList();
    }

    @Test
    void pagamentoAprovadoAvisaOLocatarioEOLocador() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-12"), tokenLocatario)).andExpect(status().isOk());

        assertEquals(1, emailsPara(locatario.get("email")).size());
        assertEquals(1, emailsPara(locador.get("email")).size());
        assertEquals(2, caixa.enviados.size());
    }

    @Test
    void emailDoLocatarioTrazComprovanteValorEFormaDePagamento() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-12"), tokenLocatario)).andExpect(status().isOk());

        var email = emailsPara(locatario.get("email")).get(0);
        assertTrue(email.assunto().contains("pagamento confirmado"));
        assertTrue(email.assunto().contains("PG-"));
        assertTrue(email.texto().contains("Apartamento 2 quartos"));
        assertTrue(email.texto().contains("12/2026"));
        assertTrue(email.texto().contains("R$ 1.800,00"));
        assertTrue(email.texto().contains("Forma de pagamento: Pix"));
        assertTrue(email.texto().contains("Comprovante: PG-"));
        assertTrue(email.texto().contains("/pagamentos.html"));
    }

    @Test
    void emailDoLocadorDizQuemPagouEOndeVerOsRecebimentos() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("BOLETO", "2026-12"), tokenLocatario)).andExpect(status().isOk());

        var email = emailsPara(locador.get("email")).get(0);
        assertTrue(email.assunto().contains("pagamento recebido"));
        assertTrue(email.texto().contains((String) locatario.get("nome")));
        assertTrue(email.texto().contains("Forma de pagamento: Boleto"));
        assertTrue(email.texto().contains("/financeiro.html"));
    }

    @Test
    void cadaFormaDePagamentoApareceComONomeCerto() throws Exception {
        Map<String, Object> debito = cobranca("CARTAO_DEBITO", "2026-12");
        debito.put("tokenCartao", "tok_teste");
        debito.put("bandeiraCartao", "debmaster");
        debito.put("parcelas", 1);
        mockMvc.perform(postJson("/pagamentos", debito, tokenLocatario)).andExpect(status().isOk());

        Map<String, Object> credito = cobranca("CARTAO_CREDITO", "2027-01");
        credito.put("tokenCartao", "tok_teste");
        credito.put("bandeiraCartao", "visa");
        credito.put("parcelas", 3);
        mockMvc.perform(postJson("/pagamentos", credito, tokenLocatario)).andExpect(status().isOk());

        List<String> textos = emailsPara(locatario.get("email")).stream().map(e -> e.texto()).toList();
        assertTrue(textos.get(0).contains("Forma de pagamento: Cartao de debito"));
        assertTrue(textos.get(1).contains("Forma de pagamento: Cartao de credito"));
    }

    @Test
    void pagamentoRecusadoNaoAvisaNinguem() throws Exception {
        Map<String, Object> debito = cobranca("CARTAO_DEBITO", "2026-12");
        debito.put("tokenCartao", "tok_teste");
        debito.put("bandeiraCartao", "debmaster");
        debito.put("parcelas", 3);

        mockMvc.perform(postJson("/pagamentos", debito, tokenLocatario)).andExpect(status().isUnprocessableEntity());

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void tentarPagarDeNovoUmMesJaPagoNaoRepeteOAviso() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-12"), tokenLocatario)).andExpect(status().isOk());
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-12"), tokenLocatario))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(2, caixa.enviados.size());
    }
}
