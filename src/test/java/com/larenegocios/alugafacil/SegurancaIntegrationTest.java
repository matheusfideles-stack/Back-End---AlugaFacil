package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.infra.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SegurancaIntegrationTest extends IntegracaoBase {

    @Autowired
    private TokenService tokenService;

    @Test
    void listagemEDetalheDeImoveisSaoPublicos() throws Exception {
        mockMvc.perform(get("/imoveis")).andExpect(status().isOk());
        mockMvc.perform(get("/imoveis/999999")).andExpect(status().isNotFound());
    }

    @Test
    void cadastrarImovelSemTokenDevolve401() throws Exception {
        mockMvc.perform(postJson("/imoveis", dadosImovel()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void desativarImovelSemTokenDevolve401() throws Exception {
        mockMvc.perform(delete("/imoveis/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void locatarioNaoPodeCadastrarImovel() throws Exception {
        String token = tokenDe("LOCATARIO");

        mockMvc.perform(postJson("/imoveis", dadosImovel(), token))
                .andExpect(status().isForbidden());
    }

    @Test
    void locadorCadastraImovelEUfFicaEmMaiusculo() throws Exception {
        String token = tokenDe("LOCADOR");

        mockMvc.perform(postJson("/imoveis", dadosImovel(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void adminTambemCadastraImovel() throws Exception {
        String token = tokenDe("ADMIN");

        mockMvc.perform(postJson("/imoveis", dadosImovel(), token))
                .andExpect(status().isOk());
    }

    @Test
    void rotasDeContratoEPagamentoExigemAutenticacao() throws Exception {
        mockMvc.perform(get("/contratos/meus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/pagamentos/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void imovelInvalidoDevolve400EmVezDe403() throws Exception {
        String token = tokenDe("LOCADOR");
        var dados = dadosImovel();
        dados.put("valorAluguel", 0);
        dados.remove("titulo");

        mockMvc.perform(postJson("/imoveis", dados, token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenLixoNaoDerrubaRotaPublicaEBarraRotaProtegida() throws Exception {
        MockHttpServletRequestBuilder lixo = get("/imoveis").header("Authorization", "Bearer isso.nao.eh-jwt");
        mockMvc.perform(lixo).andExpect(status().isOk());

        mockMvc.perform(postJson("/imoveis", dadosImovel(), "isso.nao.eh-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeUsuarioQueNaoExisteMaisDevolve401() throws Exception {
        Usuario fantasma = new Usuario();
        fantasma.setId(1L);
        fantasma.setEmail("fantasma@teste.com");
        fantasma.setPerfil(Perfil.LOCADOR);
        String token = tokenService.gerarToken(fantasma);

        mockMvc.perform(postJson("/imoveis", dadosImovel(), token))
                .andExpect(status().isUnauthorized());
    }
}
