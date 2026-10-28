package com.larenegocios.alugafacil;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvaliacaoIntegrationTest extends IntegracaoBase {

    private long imovelId;
    private String tokenLocador;

    @BeforeEach
    void criarImovel() throws Exception {
        tokenLocador = tokenDe("LOCADOR");
        imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
    }

    private Map<String, Object> avaliacao(int nota, String comentario) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nota", nota);
        if (comentario != null) {
            corpo.put("comentario", comentario);
        }
        return corpo;
    }

    private org.springframework.test.web.servlet.ResultActions avaliar(String token, Object corpo) throws Exception {
        return mockMvc.perform(put("/imoveis/" + imovelId + "/avaliacoes/minha")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json(corpo)));
    }

    private JsonNode resumo() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/imoveis/" + imovelId + "/avaliacoes/resumo"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void locatarioAvaliaComNotaEComentarioEAvaliacaoMostraQuemAvaliou() throws Exception {
        Map<String, Object> locatario = cadastrar("LOCATARIO");
        String ultimaPalavra = ((String) locatario.get("nome")).substring(((String) locatario.get("nome")).lastIndexOf(' ') + 1);

        avaliar(login(locatario), avaliacao(4, "  Casa muito bem cuidada e vizinhanca tranquila.  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(4))
                .andExpect(jsonPath("$.comentario").value("Casa muito bem cuidada e vizinhanca tranquila."))
                .andExpect(jsonPath("$.imovelId").value(imovelId))
                .andExpect(jsonPath("$.nomeUsuario").value("Fulano " + Character.toUpperCase(ultimaPalavra.charAt(0)) + "."));
    }

    @Test
    void aceitaNotaZeroECincoERecusaForaDoIntervalo() throws Exception {
        avaliar(tokenDe("LOCATARIO"), avaliacao(0, null)).andExpect(status().isOk());
        avaliar(tokenDe("LOCATARIO"), avaliacao(5, null)).andExpect(status().isOk());

        avaliar(tokenDe("LOCATARIO"), avaliacao(6, null)).andExpect(status().isBadRequest());
        avaliar(tokenDe("LOCATARIO"), avaliacao(-1, null)).andExpect(status().isBadRequest());
        avaliar(tokenDe("LOCATARIO"), Map.of("comentario", "sem nota")).andExpect(status().isBadRequest());
    }

    @Test
    void comentarioMuitoLongoEhRecusadoEComentarioEmBrancoViraNulo() throws Exception {
        avaliar(tokenDe("LOCATARIO"), avaliacao(3, "a".repeat(1001))).andExpect(status().isBadRequest());

        avaliar(tokenDe("LOCATARIO"), avaliacao(3, "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comentario").doesNotExist());
    }

    @Test
    void avaliarDeNovoAtualizaAAvaliacaoEmVezDeCriarOutra() throws Exception {
        String token = tokenDe("LOCATARIO");

        avaliar(token, avaliacao(2, "Nao gostei")).andExpect(status().isOk());
        avaliar(token, avaliacao(5, "Mudei de ideia")).andExpect(status().isOk());

        JsonNode resumo = resumo();
        assertEquals(1, resumo.get("total").asLong());
        assertEquals(5.0, resumo.get("media").asDouble());
        mockMvc.perform(get("/imoveis/" + imovelId + "/avaliacoes/minha").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comentario").value("Mudei de ideia"));
    }

    @Test
    void resumoTemMediaTotalEDistribuicaoPorNota() throws Exception {
        avaliar(tokenDe("LOCATARIO"), avaliacao(5, null)).andExpect(status().isOk());
        avaliar(tokenDe("LOCATARIO"), avaliacao(2, null)).andExpect(status().isOk());
        avaliar(tokenDe("LOCATARIO"), avaliacao(2, null)).andExpect(status().isOk());

        JsonNode resumo = resumo();
        assertEquals(3, resumo.get("total").asLong());
        assertEquals(3.0, resumo.get("media").asDouble());
        assertEquals(6, resumo.get("distribuicao").size());
        assertEquals(2, resumo.get("distribuicao").get(2).asLong());
        assertEquals(1, resumo.get("distribuicao").get(5).asLong());
        assertEquals(0, resumo.get("distribuicao").get(0).asLong());
    }

    @Test
    void imovelSemAvaliacoesTemTotalZeroESemMedia() throws Exception {
        JsonNode resumo = resumo();

        assertEquals(0, resumo.get("total").asLong());
        assertTrue(resumo.path("media").isMissingNode());
        assertEquals(6, resumo.get("distribuicao").size());
    }

    @Test
    void listaDeAvaliacoesEPublicaEMostraAsMaisRecentesPrimeiro() throws Exception {
        String primeiro = tokenDe("LOCATARIO");
        avaliar(primeiro, avaliacao(3, "Primeira")).andExpect(status().isOk());
        Thread.sleep(20);
        avaliar(tokenDe("LOCATARIO"), avaliacao(4, "Segunda")).andExpect(status().isOk());

        mockMvc.perform(get("/imoveis/" + imovelId + "/avaliacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].comentario").value("Segunda"))
                .andExpect(jsonPath("$.content[1].comentario").value("Primeira"))
                .andExpect(jsonPath("$.content[0].nomeUsuario").isNotEmpty());
    }

    @Test
    void somenteLocatarioLogadoPodeAvaliar() throws Exception {
        mockMvc.perform(put("/imoveis/" + imovelId + "/avaliacoes/minha")
                        .contentType("application/json").content(json(avaliacao(5, null))))
                .andExpect(status().isUnauthorized());

        avaliar(tokenLocador, avaliacao(5, "Meu proprio imovel")).andExpect(status().isForbidden());
        avaliar(tokenDe("LOCADOR"), avaliacao(1, "Concorrente")).andExpect(status().isForbidden());
        avaliar(login(cadastrarAdmin()), avaliacao(1, null)).andExpect(status().isForbidden());
        assertEquals(0, resumo().get("total").asLong());
    }

    @Test
    void imovelInexistenteDevolve404() throws Exception {
        mockMvc.perform(put("/imoveis/999999/avaliacoes/minha")
                        .header("Authorization", "Bearer " + tokenDe("LOCATARIO"))
                        .contentType("application/json").content(json(avaliacao(5, null))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/imoveis/999999/avaliacoes")).andExpect(status().isNotFound());
        mockMvc.perform(get("/imoveis/999999/avaliacoes/resumo")).andExpect(status().isNotFound());
    }

    @Test
    void minhaAvaliacaoDevolve404AteAvaliar() throws Exception {
        String token = tokenDe("LOCATARIO");

        mockMvc.perform(get("/imoveis/" + imovelId + "/avaliacoes/minha").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/imoveis/" + imovelId + "/avaliacoes/minha")).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioApagaAPropriaAvaliacao() throws Exception {
        String token = tokenDe("LOCATARIO");
        avaliar(token, avaliacao(1, "Ruim")).andExpect(status().isOk());

        mockMvc.perform(delete("/imoveis/" + imovelId + "/avaliacoes/minha").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, resumo().get("total").asLong());
        mockMvc.perform(delete("/imoveis/" + imovelId + "/avaliacoes/minha").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void soAdminApagaAvaliacaoDeOutraPessoa() throws Exception {
        String token = tokenDe("LOCATARIO");
        long id = objectMapper.readTree(avaliar(token, avaliacao(1, "Spam"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/avaliacoes/" + id).header("Authorization", "Bearer " + tokenDe("LOCATARIO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/avaliacoes/" + id).header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/avaliacoes/" + id).header("Authorization", "Bearer " + login(cadastrarAdmin())))
                .andExpect(status().isNoContent());

        assertEquals(0, resumo().get("total").asLong());
        mockMvc.perform(delete("/avaliacoes/" + id).header("Authorization", "Bearer " + login(cadastrarAdmin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void imovelTrazMediaETotalNaListagemENoDetalhe() throws Exception {
        avaliar(tokenDe("LOCATARIO"), avaliacao(5, null)).andExpect(status().isOk());
        avaliar(tokenDe("LOCATARIO"), avaliacao(4, null)).andExpect(status().isOk());

        mockMvc.perform(get("/imoveis/" + imovelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAvaliacoes").value(4.5))
                .andExpect(jsonPath("$.totalAvaliacoes").value(2));
        mockMvc.perform(get("/imoveis").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + imovelId + ")].mediaAvaliacoes").value(4.5))
                .andExpect(jsonPath("$.content[?(@.id == " + imovelId + ")].totalAvaliacoes").value(2));
    }

    @Test
    void imovelSemAvaliacaoTemTotalZeroSemMedia() throws Exception {
        JsonNode imovel = objectMapper.readTree(mockMvc.perform(get("/imoveis/" + imovelId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertEquals(0, imovel.get("totalAvaliacoes").asLong());
        assertTrue(imovel.get("mediaAvaliacoes").isNull());
    }
}
