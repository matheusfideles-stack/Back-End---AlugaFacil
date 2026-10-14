package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacaoIntegrationTest extends IntegracaoBase {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void registraUsuarioSemDevolverASenha() throws Exception {
        Map<String, Object> dados = dadosUsuario("LOCADOR");

        mockMvc.perform(postJson("/auth/registro", dados))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(dados.get("email")))
                .andExpect(jsonPath("$.perfil").value("LOCADOR"))
                .andExpect(content().string(not(containsString("senha"))));
    }

    @Test
    void guardaASenhaComHashBcrypt() throws Exception {
        Map<String, Object> dados = cadastrar("LOCATARIO");

        String armazenada = usuarioRepository.findByEmail((String) dados.get("email")).orElseThrow().getPassword();

        assertFalse(armazenada.contains(SENHA));
        assertTrue(armazenada.startsWith("$2"), "esperava hash bcrypt, veio: " + armazenada);
    }

    @Test
    void rejeitaCadastroDuplicadoComEmailOuCpfJaUsado() throws Exception {
        Map<String, Object> dados = cadastrar("LOCATARIO");

        mockMvc.perform(postJson("/auth/registro", dados))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejeitaCadastroInvalidoComListaDeErros() throws Exception {
        Map<String, Object> dados = dadosUsuario("LOCADOR");
        dados.put("email", "isso-nao-e-email");
        dados.put("senha", "curta");
        dados.remove("perfil");

        mockMvc.perform(postJson("/auth/registro", dados))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[?(@.campo == 'email')]").exists())
                .andExpect(jsonPath("$[?(@.campo == 'senha')]").exists())
                .andExpect(jsonPath("$[?(@.campo == 'perfil')]").exists());
    }

    @Test
    void loginComCredenciaisCertasDevolveToken() throws Exception {
        Map<String, Object> dados = cadastrar("LOCADOR");

        mockMvc.perform(postJson("/auth/login", Map.of("email", dados.get("email"), "senha", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginComSenhaErradaDevolve401() throws Exception {
        Map<String, Object> dados = cadastrar("LOCADOR");

        mockMvc.perform(postJson("/auth/login", Map.of("email", dados.get("email"), "senha", "errada123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDeUsuarioInexistenteDevolve401() throws Exception {
        mockMvc.perform(postJson("/auth/login", Map.of("email", "fantasma@teste.com", "senha", SENHA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaInexistenteDevolve404EmVezDe403() throws Exception {
        mockMvc.perform(postJson("/auth/registrar", dadosUsuario("LOCADOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/rota-que-nao-existe").header("Authorization", "Bearer " + tokenDe("LOCATARIO")))
                .andExpect(status().isNotFound());
    }
}
