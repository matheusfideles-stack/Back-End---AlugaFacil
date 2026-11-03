package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Hoje e 20/12/2026 e o contrato vence todo dia 10: o aluguel de dezembro esta com 10 dias de atraso.
@TestPropertySource(properties = "app.relogio.fixo-em=2026-12-20")
class MultaEJurosIntegrationTest extends IntegracaoBase {

    private String tokenLocatario;
    private String tokenLocador;
    private long contratoId;

    @BeforeEach
    void preparar() throws Exception {
        tokenLocador = tokenDe("LOCADOR");
        tokenLocatario = tokenDe("LOCATARIO");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = contratar(imovelId, tokenLocatario, tokenLocador);
    }

    private Map<String, Object> cobranca(String mes) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", mes);
        dados.put("metodo", "PIX");
        return dados;
    }

    @Test
    void valorAtualizadoMostraMultaJurosETotalDoMesEmAtraso() throws Exception {
        mockMvc.perform(get("/pagamentos/valor-atualizado?contratoId=" + contratoId + "&referenciaMes=2026-12")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasEmAtraso").value(10))
                .andExpect(jsonPath("$.valorBase").value(1800.00))
                .andExpect(jsonPath("$.multa").value(36.00))
                .andExpect(jsonPath("$.juros").value(6.00))
                .andExpect(jsonPath("$.total").value(1842.00));
    }

    @Test
    void mesQueAindaNaoVenceuNaoTemAcrescimo() throws Exception {
        mockMvc.perform(get("/pagamentos/valor-atualizado?contratoId=" + contratoId + "&referenciaMes=2027-01")
                        .header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.diasEmAtraso").value(0))
                .andExpect(jsonPath("$.total").value(1800.00));
    }

    @Test
    void cobrancaDoMesEmAtrasoJaSaiComMultaEJuros() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("2026-12"), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(1842.00))
                .andExpect(jsonPath("$.multa").value(36.00))
                .andExpect(jsonPath("$.juros").value(6.00));
    }

    @Test
    void cobrancaDeMesFuturoSaiPeloValorNormal() throws Exception {
        mockMvc.perform(postJson("/pagamentos", cobranca("2027-02"), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(1800.00))
                .andExpect(jsonPath("$.multa").value(0))
                .andExpect(jsonPath("$.juros").value(0));
    }

    @Test
    void soQuemParticipaDoContratoConsultaOValor() throws Exception {
        mockMvc.perform(get("/pagamentos/valor-atualizado?contratoId=" + contratoId + "&referenciaMes=2026-12")
                        .header("Authorization", "Bearer " + tokenDe("LOCATARIO")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/pagamentos/valor-atualizado?contratoId=" + contratoId + "&referenciaMes=2026-12"))
                .andExpect(status().isUnauthorized());
    }
}
