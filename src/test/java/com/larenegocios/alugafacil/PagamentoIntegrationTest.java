package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo de pagamento usando o gateway falso (sem chamar o Mercado Pago). */
class PagamentoIntegrationTest extends IntegracaoBase {

    private String tokenLocatario;
    private long contratoId;

    @BeforeEach
    void criarContrato() throws Exception {
        String tokenLocador = tokenDe("LOCADOR");
        tokenLocatario = tokenDe("LOCATARIO");

        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = idDaResposta(mockMvc.perform(postJson("/contratos", dadosContrato(imovelId), tokenLocatario)));
    }

    private Map<String, Object> cobranca(String metodo, String referenciaMes) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", referenciaMes);
        dados.put("metodo", metodo);
        return dados;
    }

    @Test
    void geraCobrancaPixComCopiaECola() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-10"), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("PIX"))
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.valor").value(1800.00))
                .andExpect(jsonPath("$.pixCopiaECola", startsWith("00020126FAKE")))
                .andExpect(jsonPath("$.vencimento").value("2026-10-10"));
    }

    @Test
    void geraBoletoComLinhaDigitavel() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("BOLETO", "2026-11"), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("BOLETO"))
                .andExpect(jsonPath("$.linhaDigitavel").isNotEmpty());
    }

    @Test
    void naoCobraDuasVezesOMesmoMes() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-12"), tokenLocatario))
                .andExpect(status().isOk());

        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2026-12"), tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cartaoSemTokenEhRecusadoPelaRegraDeNegocio() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("CARTAO_CREDITO", "2027-01"), tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void debitoNaoPermiteParcelamento() throws Exception {
        Map<String, Object> dados = cobranca("CARTAO_DEBITO", "2027-02");
        dados.put("tokenCartao", "tok_teste");
        dados.put("bandeiraCartao", "debmaster");
        dados.put("parcelas", 3);

        mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void referenciaMesForaDoFormatoDevolve400() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "10/2026"), tokenLocatario))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usuarioForaDoContratoNaoPodeGerarCobranca() throws Exception {
        String intruso = tokenDe("LOCATARIO");

        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2027-03"), intruso))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void semTokenNaoGeraCobranca() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2027-04")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sincronizaEEstornaPagamentoConfirmado() throws Exception {
        long pagamentoId = idDaResposta(
                mockMvc.perform(postJson("/pagamentos", cobranca("PIX", "2027-05"), tokenLocatario)));

        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/sincronizar")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"));

        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/estornar")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pagamentos/" + pagamentoId)
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"));

        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/estornar")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pagamentoInexistenteDevolve404() throws Exception {
        mockMvc.perform(get("/pagamentos/999999").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isNotFound());
    }
}
