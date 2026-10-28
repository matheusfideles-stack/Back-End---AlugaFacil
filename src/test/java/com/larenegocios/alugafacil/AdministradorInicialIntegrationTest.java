package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.infra.security.AdministradorInicial;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"app.admin.email=Admin.Teste@Alugafacil.local", "app.admin.senha=SenhaAdmin1"})
class AdministradorInicialIntegrationTest extends IntegracaoBase {

    @Autowired
    private AdministradorInicial administradorInicial;

    @Test
    void administradorConfiguradoEntraComPerfilAdmin() throws Exception {
        mockMvc.perform(postJson("/auth/login", Map.of("email", "admin.teste@alugafacil.local", "senha", "SenhaAdmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"));
    }

    @Test
    void subirDeNovoNaoDuplicaOAdministrador() {
        administradorInicial.run(null);
        administradorInicial.run(null);

        long quantos = usuarioRepository.findByPerfil(Perfil.ADMIN, org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(u -> u.getEmail().equals("admin.teste@alugafacil.local")).count();
        assertEquals(1, quantos);
    }

    @Test
    void senhaDoAdministradorFicaComHash() {
        String armazenada = usuarioRepository.findUsuarioByEmail("admin.teste@alugafacil.local").orElseThrow().getSenha();
        assertEquals(true, armazenada.startsWith("$2"));
    }
}
