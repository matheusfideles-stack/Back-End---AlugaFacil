package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImovelContratoIntegrationTest extends IntegracaoBase {

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long criarImovel(String tokenLocador) throws Exception {
        return idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
    }

    @Test
    void imovelGuardaQuartosBanheirosVagasEArea() throws Exception {
        String token = tokenDe("LOCADOR");
        Map<String, Object> dados = dadosImovel();
        dados.put("quartos", 3);
        dados.put("banheiros", 2);
        dados.put("vagas", 1);
        dados.put("areaM2", 90);

        long id = idDaResposta(mockMvc.perform(postJson("/imoveis", dados, token)));

        mockMvc.perform(get("/imoveis/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quartos").value(3))
                .andExpect(jsonPath("$.banheiros").value(2))
                .andExpect(jsonPath("$.vagas").value(1))
                .andExpect(jsonPath("$.areaM2").value(90))
                .andExpect(jsonPath("$.complemento").value("Apto 101"));
    }

    @Test
    void donoAtualizaOImovel() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = criarImovel(token);
        Map<String, Object> dados = dadosImovel();
        dados.put("titulo", "Titulo novo");
        dados.put("valorAluguel", 2100.50);

        mockMvc.perform(put("/imoveis/" + id).header("Authorization", bearer(token))
                        .contentType("application/json").content(json(dados)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/imoveis/" + id))
                .andExpect(jsonPath("$.titulo").value("Titulo novo"))
                .andExpect(jsonPath("$.valorAluguel").value(2100.50));
    }

    @Test
    void outroLocadorNaoAtualizaNemDesativa() throws Exception {
        long id = criarImovel(tokenDe("LOCADOR"));
        String intruso = tokenDe("LOCADOR");

        mockMvc.perform(put("/imoveis/" + id).header("Authorization", bearer(intruso))
                        .contentType("application/json").content(json(dadosImovel())))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(delete("/imoveis/" + id).header("Authorization", bearer(intruso)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void locatarioNaoAtualizaImovel() throws Exception {
        long id = criarImovel(tokenDe("LOCADOR"));

        mockMvc.perform(put("/imoveis/" + id).header("Authorization", bearer(tokenDe("LOCATARIO")))
                        .contentType("application/json").content(json(dadosImovel())))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativarImovelRealmenteSaiDaVitrine() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = criarImovel(token);

        mockMvc.perform(get("/imoveis?size=200"))
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) id)));

        mockMvc.perform(delete("/imoveis/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/imoveis?size=200"))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) id))));
        mockMvc.perform(get("/imoveis/meus").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].disponivel").value(false));
    }

    @Test
    void imovelDesativadoPodeSerReativado() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = criarImovel(token);
        mockMvc.perform(delete("/imoveis/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/imoveis/" + id + "/reativar").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/imoveis/" + id)).andExpect(jsonPath("$.disponivel").value(true));
    }

    @Test
    void imovelComContratoAtivoNaoPodeSerReativado() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = criarImovel(token);
        contratar(id, tokenDe("LOCATARIO"), token);

        mockMvc.perform(put("/imoveis/" + id + "/reativar").header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void encerrarContratoLiberaOImovelDeNovo() throws Exception {
        String tokenLocatario = tokenDe("LOCATARIO");
        String tokenLocador = tokenDe("LOCADOR");
        long imovelId = criarImovel(tokenLocador);
        long contratoId = contratar(imovelId, tokenLocatario, tokenLocador);

        mockMvc.perform(put("/contratos/" + contratoId + "/encerrar").header("Authorization", bearer(tokenLocatario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/imoveis?size=200"))
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) imovelId)));
        mockMvc.perform(get("/contratos/" + contratoId).header("Authorization", bearer(tokenLocatario)))
                .andExpect(jsonPath("$.status").value("ENCERRADO"));
    }

    @Test
    void meusImoveisListaSoOsDoLocadorLogado() throws Exception {
        String token = tokenDe("LOCADOR");
        long meu = criarImovel(token);
        criarImovel(tokenDe("LOCADOR"));

        mockMvc.perform(get("/imoveis/meus").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) meu));
    }

    @Test
    void meusImoveisExigeLoginELocador() throws Exception {
        mockMvc.perform(get("/imoveis/meus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/imoveis/meus").header("Authorization", bearer(tokenDe("LOCATARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void locadorNaoEnviaPropostaDeAluguel() throws Exception {
        String token = tokenDe("LOCADOR");
        long id = criarImovel(token);

        mockMvc.perform(postJson("/propostas", dadosContrato(id), token))
                .andExpect(status().isForbidden());
    }

    @Test
    void contratoMostraDadosDoLocatarioParaOLocador() throws Exception {
        Map<String, Object> locatario = cadastrar("LOCATARIO");
        String tokenLocatario = login(locatario);
        String tokenLocador = tokenDe("LOCADOR");
        long imovelId = criarImovel(tokenLocador);

        contratar(imovelId, tokenLocatario, tokenLocador);

        mockMvc.perform(get("/contratos/meus?papel=locador").header("Authorization", bearer(tokenLocador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].emailLocatario").value(locatario.get("email")))
                .andExpect(jsonPath("$.content[0].nomeLocatario").value(locatario.get("nome")));
    }

    @Test
    void imovelAlugadoSaiDaVitrineEContratoApareceParaOLocatario() throws Exception {
        String tokenLocatario = tokenDe("LOCATARIO");
        String tokenLocador = tokenDe("LOCADOR");
        long imovelId = criarImovel(tokenLocador);

        contratar(imovelId, tokenLocatario, tokenLocador);

        mockMvc.perform(get("/imoveis?size=200"))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) imovelId))));
        mockMvc.perform(get("/contratos/meus").header("Authorization", bearer(tokenLocatario)))
                .andExpect(jsonPath("$.content[0].imovelId").value((int) imovelId))
                .andExpect(jsonPath("$.content[0].status").value("ATIVO"));
    }
}
