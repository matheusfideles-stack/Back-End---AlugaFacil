package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O gateway deste teste deixa o pagamento pendente ate ser consultado, para provar que o recibo so sai depois de pago.
@Import(PagamentoPendenteEmailIntegrationTest.GatewayQueConfirmaDepois.class)
class PdfIntegrationTest extends IntegracaoBase {

    private String tokenLocador;
    private String tokenLocatario;
    private long contratoId;

    @BeforeEach
    void preparar() throws Exception {
        tokenLocador = tokenDe("LOCADOR");
        tokenLocatario = tokenDe("LOCATARIO");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = contratar(imovelId, tokenLocatario, tokenLocador);
    }

    private long gerarPix(String mes) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", mes);
        dados.put("metodo", "PIX");
        return idDaResposta(mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario)).andExpect(status().isOk()));
    }

    private byte[] baixar(String caminho, String token) throws Exception {
        return mockMvc.perform(get(caminho).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void locatarioELocadorBaixamOContratoEmPdf() throws Exception {
        byte[] doLocatario = baixar("/contratos/" + contratoId + "/contrato.pdf", tokenLocatario);
        byte[] doLocador = baixar("/contratos/" + contratoId + "/contrato.pdf", tokenLocador);

        assertTrue(new String(doLocatario, 0, 5).startsWith("%PDF"));
        assertTrue(new String(doLocador, 0, 5).startsWith("%PDF"));
    }

    @Test
    void arquivoVemComoAnexoComONomeDoContrato() throws Exception {
        mockMvc.perform(get("/contratos/" + contratoId + "/contrato.pdf").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"contrato-" + contratoId + ".pdf\""));
    }

    @Test
    void quemNaoParticipaDoContratoNaoBaixaNemSemLogin() throws Exception {
        mockMvc.perform(get("/contratos/" + contratoId + "/contrato.pdf").header("Authorization", "Bearer " + tokenDe("LOCATARIO")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/contratos/" + contratoId + "/contrato.pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void contratoInexistenteDaNaoEncontrado() throws Exception {
        mockMvc.perform(get("/contratos/999999/contrato.pdf").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isNotFound());
    }

    @Test
    void reciboSoExisteDepoisDoPagamentoConfirmado() throws Exception {
        long pagamentoId = gerarPix("2026-12");

        mockMvc.perform(get("/pagamentos/" + pagamentoId + "/recibo.pdf").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/sincronizar").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk());

        byte[] recibo = baixar("/pagamentos/" + pagamentoId + "/recibo.pdf", tokenLocatario);
        assertTrue(new String(recibo, 0, 5).startsWith("%PDF"));
        assertEquals("attachment; filename=\"recibo-PG-" + String.format("%06d", pagamentoId) + ".pdf\"",
                mockMvc.perform(get("/pagamentos/" + pagamentoId + "/recibo.pdf").header("Authorization", "Bearer " + tokenLocador))
                        .andReturn().getResponse().getHeader("Content-Disposition"));
    }

    @Test
    void reciboNaoVaiParaQuemNaoParticipa() throws Exception {
        long pagamentoId = gerarPix("2026-12");
        mockMvc.perform(post("/pagamentos/" + pagamentoId + "/sincronizar").header("Authorization", "Bearer " + tokenLocatario));

        mockMvc.perform(get("/pagamentos/" + pagamentoId + "/recibo.pdf").header("Authorization", "Bearer " + tokenDe("LOCATARIO")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/pagamentos/" + pagamentoId + "/recibo.pdf")).andExpect(status().isUnauthorized());
    }
}
