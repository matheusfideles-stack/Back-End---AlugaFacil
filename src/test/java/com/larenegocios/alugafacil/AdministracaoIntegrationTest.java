package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdministracaoIntegrationTest extends IntegracaoBase {

    private MockHttpServletRequestBuilder situacao(String caminho, boolean ativo, String token) {
        return put(caminho).header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"ativo\":" + ativo + "}");
    }

    @Test
    void adminListaUsuariosComSituacaoEFiltraPorPerfil() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        cadastrar("LOCATARIO");

        mockLocatarios(tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].perfil").value("LOCATARIO"))
                .andExpect(jsonPath("$.content[0].ativo").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions mockLocatarios(String token) throws Exception {
        return mockMvc.perform(get("/admin/usuarios?perfil=LOCATARIO&size=200").header("Authorization", "Bearer " + token));
    }

    @Test
    void contaDesativadaNaoEntraNemUsaOTokenJaEmitido() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        Map<String, Object> locatario = cadastrar("LOCATARIO");
        String tokenLocatario = login(locatario);
        long id = idDoUsuario((String) locatario.get("email"), tokenAdmin);

        mockMvc.perform(get("/contratos/meus?papel=locatario").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk());

        mockMvc.perform(situacao("/admin/usuarios/" + id + "/ativo", false, tokenAdmin)).andExpect(status().isNoContent());

        mockMvc.perform(get("/contratos/meus?papel=locatario").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(postJson("/auth/login", Map.of("email", locatario.get("email"), "senha", locatario.get("senha"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reativarDevolveOAcessoEAdminNaoDesativaASiMesmo() throws Exception {
        Map<String, Object> admin = cadastrar("ADMIN");
        String tokenAdmin = login(admin);
        Map<String, Object> locatario = cadastrar("LOCATARIO");
        long id = idDoUsuario((String) locatario.get("email"), tokenAdmin);

        mockMvc.perform(situacao("/admin/usuarios/" + id + "/ativo", false, tokenAdmin)).andExpect(status().isNoContent());
        mockMvc.perform(situacao("/admin/usuarios/" + id + "/ativo", true, tokenAdmin)).andExpect(status().isNoContent());
        mockMvc.perform(postJson("/auth/login", Map.of("email", locatario.get("email"), "senha", locatario.get("senha"))))
                .andExpect(status().isOk());

        long idAdmin = idDoUsuario((String) admin.get("email"), tokenAdmin);
        mockMvc.perform(situacao("/admin/usuarios/" + idAdmin + "/ativo", false, tokenAdmin))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminDesativaImovelQueSomeDaBuscaEVoltaComReativar() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        String tokenLocador = tokenDe("LOCADOR");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));

        mockMvc.perform(situacao("/admin/imoveis/" + imovelId + "/disponivel", false, tokenAdmin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/imoveis/" + imovelId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(false));

        mockMvc.perform(situacao("/admin/imoveis/" + imovelId + "/disponivel", true, tokenAdmin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/imoveis/" + imovelId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(true));
    }

    @Test
    void adminListaImoveisAvaliacoesContratosEPagamentos() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        String tokenLocador = tokenDe("LOCADOR");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));

        mockMvc.perform(get("/admin/imoveis").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/admin/avaliacoes").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/admin/contratos").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/admin/pagamentos").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void avaliacaoAparecePeloAdminEPodeSerApagada() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        String tokenLocador = tokenDe("LOCADOR");
        String tokenLocatario = tokenDe("LOCATARIO");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/imoveis/" + imovelId + "/avaliacoes/minha")
                        .header("Authorization", "Bearer " + tokenLocatario)
                        .contentType("application/json")
                        .content("{\"nota\":2,\"comentario\":\"Avaliacao para moderar\"}"))
                .andExpect(status().isOk());

        String resposta = mockMvc.perform(get("/admin/avaliacoes?size=200&sort=id,desc")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].comentario").value("Avaliacao para moderar"))
                .andReturn().getResponse().getContentAsString();
        long avaliacaoId = objectMapper.readTree(resposta).get("content").get(0).get("id").asLong();

        mockMvc.perform(delete("/avaliacoes/" + avaliacaoId).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    void visaoDoAdminNaoEstaDisponivelParaOsOutrosPerfis() throws Exception {
        String tokenLocatario = tokenDe("LOCATARIO");
        String tokenLocador = tokenDe("LOCADOR");
        for (String caminho : new String[]{"/admin/usuarios", "/admin/imoveis", "/admin/avaliacoes",
                "/admin/contratos", "/admin/pagamentos"}) {
            mockMvc.perform(get(caminho).header("Authorization", "Bearer " + tokenLocatario))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(caminho).header("Authorization", "Bearer " + tokenLocador))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(situacao("/admin/usuarios/1/ativo", false, tokenLocatario)).andExpect(status().isForbidden());
    }

    private long idDoUsuario(String email, String tokenAdmin) throws Exception {
        String resposta = mockMvc.perform(get("/admin/usuarios?size=500").header("Authorization", "Bearer " + tokenAdmin))
                .andReturn().getResponse().getContentAsString();
        for (var no : objectMapper.readTree(resposta).get("content")) {
            if (email.equals(no.get("email").asText())) {
                return no.get("id").asLong();
            }
        }
        throw new AssertionError("usuario nao encontrado na listagem: " + email);
    }
}
