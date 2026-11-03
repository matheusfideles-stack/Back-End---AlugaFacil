package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComodidadesIntegrationTest extends IntegracaoBase {

    private Map<String, Object> dadosCom(Object comodidades) {
        Map<String, Object> dados = dadosImovel();
        dados.put("comodidades", comodidades);
        return dados;
    }

    @Test
    void imovelNasceComAsComodidadesEscolhidasEmOrdemFixa() throws Exception {
        String token = tokenDe("LOCADOR");

        mockMvc.perform(postJson("/imoveis", dadosCom(List.of("TV", "PISCINA", "LAREIRA")), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comodidades", contains("PISCINA", "LAREIRA", "TV")));
    }

    @Test
    void imovelSemComodidadesDevolveListaVazia() throws Exception {
        String token = tokenDe("LOCADOR");

        mockMvc.perform(postJson("/imoveis", dadosImovel(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comodidades", empty()));
    }

    @Test
    void comodidadeRepetidaContaUmaVez() throws Exception {
        String token = tokenDe("LOCADOR");

        mockMvc.perform(postJson("/imoveis", dadosCom(List.of("PISCINA", "PISCINA")), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comodidades", contains("PISCINA")));
    }

    @Test
    void comodidadeInventadaEhRecusada() throws Exception {
        String token = tokenDe("LOCADOR");

        mockMvc.perform(postJson("/imoveis", dadosCom(List.of("HELIPONTO")), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarTrocaAsComodidadesEAListaPublicaMostra() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosCom(List.of("PISCINA")), token)));

        mockMvc.perform(put("/imoveis/" + id).header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json(dadosCom(List.of("CHURRASQUEIRA", "AR_CONDICIONADO")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comodidades", contains("AR_CONDICIONADO", "CHURRASQUEIRA")));

        mockMvc.perform(get("/imoveis/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comodidades", contains("AR_CONDICIONADO", "CHURRASQUEIRA")));
    }

    @Test
    void editarSemEnviarOCampoMantemAsComodidades() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosCom(List.of("PISCINA", "TV")), token)));

        mockMvc.perform(put("/imoveis/" + id).header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json(dadosImovel())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comodidades", contains("PISCINA", "TV")));
    }

    @Test
    void imovelSemEnderecoLocalizadoNaoTemPosicaoMasAindaEPublicado() throws Exception {
        String token = tokenDe("LOCADOR");

        // Nos testes o geocodificador esta desligado: o imovel e salvo, so sem latitude e longitude.
        mockMvc.perform(postJson("/imoveis", dadosImovel(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist());
    }
}
