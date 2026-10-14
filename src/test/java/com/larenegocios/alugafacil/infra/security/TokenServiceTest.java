package com.larenegocios.alugafacil.infra.security;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.infra.exception.TokenJWTInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private TokenService tokenService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        tokenService = criar("segredo-de-teste", 2);

        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("maria@teste.com");
        usuario.setPerfil(Perfil.LOCADOR);
    }

    private TokenService criar(String segredo, long horas) {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "secret", segredo);
        ReflectionTestUtils.setField(service, "expirationHours", horas);
        return service;
    }

    @Test
    void tokenGeradoDevolveOEmailComoSubject() {
        String token = tokenService.gerarToken(usuario);

        assertEquals("maria@teste.com", tokenService.getSubject(token));
    }

    @Test
    void rejeitaTokenAssinadoComOutroSegredo() {
        String token = criar("segredo-de-outro-sistema", 2).gerarToken(usuario);

        assertThrows(TokenJWTInvalidoException.class, () -> tokenService.getSubject(token));
    }

    @Test
    void rejeitaTokenExpirado() {
        String expirado = criar("segredo-de-teste", -1).gerarToken(usuario);

        assertThrows(TokenJWTInvalidoException.class, () -> tokenService.getSubject(expirado));
    }

    @Test
    void rejeitaTokenAdulteradoOuLixo() {
        String token = tokenService.gerarToken(usuario);
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(TokenJWTInvalidoException.class, () -> tokenService.getSubject(adulterado));
        assertThrows(TokenJWTInvalidoException.class, () -> tokenService.getSubject("isso.nao.eh-jwt"));
    }
}
