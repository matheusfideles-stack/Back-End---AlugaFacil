package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManutencaoIntegrationTest extends IntegracaoBase {

    private String tokenLocador;
    private String tokenLocatario;
    private long contratoId;

    @BeforeEach
    void criarContrato() throws Exception {
        tokenLocador = tokenDe("LOCADOR");
        tokenLocatario = tokenDe("LOCATARIO");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = contratar(imovelId, tokenLocatario, tokenLocador);
    }

    private Map<String, Object> pedido() {
        return Map.of("contratoId", contratoId, "tipo", "Hidráulica", "urgencia", "URGENTE",
                "descricao", "Vazamento embaixo da pia da cozinha");
    }

    private long solicitar() throws Exception {
        return idDaResposta(mockMvc.perform(postJson("/manutencoes", pedido(), tokenLocatario)));
    }

    @Test
    void locatarioSolicitaEGanhaProtocolo() throws Exception {
        mockMvc.perform(postJson("/manutencoes", pedido(), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolo", startsWith("MAN-")))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.tituloImovel").value("Apartamento 2 quartos"));
    }

    @Test
    void locatarioVeAsProprias_ELocadorVeAsRecebidas() throws Exception {
        long id = solicitar();

        mockMvc.perform(get("/manutencoes/meus").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content[0].id").value((int) id));
        mockMvc.perform(get("/manutencoes/recebidas").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(jsonPath("$.content[0].id").value((int) id))
                .andExpect(jsonPath("$.content[0].nomeLocatario").isNotEmpty());
    }

    @Test
    void locadorAtualizaOStatus() throws Exception {
        long id = solicitar();

        mockMvc.perform(put("/manutencoes/" + id + "/status").header("Authorization", "Bearer " + tokenLocador)
                        .contentType("application/json").content(json(Map.of("status", "EM_ANDAMENTO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        mockMvc.perform(get("/manutencoes/meus").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content[0].status").value("EM_ANDAMENTO"));
    }

    @Test
    void locatarioNaoMudaOStatus() throws Exception {
        long id = solicitar();

        mockMvc.perform(put("/manutencoes/" + id + "/status").header("Authorization", "Bearer " + tokenLocatario)
                        .contentType("application/json").content(json(Map.of("status", "CONCLUIDA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void outroLocadorNaoMudaOStatus() throws Exception {
        long id = solicitar();

        mockMvc.perform(put("/manutencoes/" + id + "/status").header("Authorization", "Bearer " + tokenDe("LOCADOR"))
                        .contentType("application/json").content(json(Map.of("status", "CONCLUIDA"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void soOLocatarioDoContratoPodeSolicitar() throws Exception {
        mockMvc.perform(postJson("/manutencoes", pedido(), tokenDe("LOCATARIO")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(postJson("/manutencoes", pedido(), tokenLocador))
                .andExpect(status().isForbidden());
    }

    @Test
    void descricaoCurtaDevolve400() throws Exception {
        mockMvc.perform(postJson("/manutencoes",
                        Map.of("contratoId", contratoId, "tipo", "Outro", "urgencia", "NORMAL", "descricao", "curta"),
                        tokenLocatario))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semLoginDevolve401() throws Exception {
        mockMvc.perform(postJson("/manutencoes", pedido())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/manutencoes/recebidas")).andExpect(status().isUnauthorized());
    }

    @Test
    void contratoEncerradoNaoAceitaSolicitacao() throws Exception {
        mockMvc.perform(put("/contratos/" + contratoId + "/encerrar").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isNoContent());

        mockMvc.perform(postJson("/manutencoes", pedido(), tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }
}
