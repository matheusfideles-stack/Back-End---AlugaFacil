package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends IntegracaoBase {

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void adminCadastraProprietarioQueEntraEPublicaImovel() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        Map<String, Object> locador = dadosUsuario("LOCADOR");

        mockMvc.perform(postJson("/admin/locadores", locador, tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("LOCADOR"))
                .andExpect(jsonPath("$.email").value(locador.get("email")));

        confirmarNoBanco((String) locador.get("email"));
        String tokenLocador = login(locador);
        mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)).andExpect(status().isOk());
    }

    @Test
    void perfilEnviadoNoCorpoNaoVirouAdmin() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        Map<String, Object> dados = dadosUsuario("ADMIN");

        mockMvc.perform(postJson("/admin/locadores", dados, tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("LOCADOR"));
    }

    @Test
    void locatarioELocadorNaoCadastramProprietario() throws Exception {
        mockMvc.perform(postJson("/admin/locadores", dadosUsuario("LOCADOR"), tokenDe("LOCATARIO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson("/admin/locadores", dadosUsuario("LOCADOR"), tokenDe("LOCADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginDevolve401() throws Exception {
        mockMvc.perform(postJson("/admin/locadores", dadosUsuario("LOCADOR"))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/locadores")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailOuCpfRepetidoDevolve422() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        Map<String, Object> locador = dadosUsuario("LOCADOR");
        mockMvc.perform(postJson("/admin/locadores", locador, tokenAdmin)).andExpect(status().isOk());

        mockMvc.perform(postJson("/admin/locadores", locador, tokenAdmin)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void dadosInvalidosDevolve400() throws Exception {
        Map<String, Object> locador = dadosUsuario("LOCADOR");
        locador.put("email", "nao-e-email");

        mockMvc.perform(postJson("/admin/locadores", locador, tokenDe("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminListaSoOsProprietarios() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        Map<String, Object> locador = dadosUsuario("LOCADOR");
        mockMvc.perform(postJson("/admin/locadores", locador, tokenAdmin)).andExpect(status().isOk());
        cadastrar("LOCATARIO");

        mockMvc.perform(get("/admin/locadores?size=200").header("Authorization", bearer(tokenAdmin))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.perfil != 'LOCADOR')]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.email == '" + locador.get("email") + "')]").isNotEmpty());
    }
}
