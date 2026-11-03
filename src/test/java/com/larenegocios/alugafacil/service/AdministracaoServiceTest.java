package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdministracaoServiceTest {

    private final Usuario admin = Dobles.usuario(1, Perfil.ADMIN);
    private final Usuario locatario = Dobles.usuario(2, Perfil.LOCATARIO);

    private AdministracaoService service;

    @BeforeEach
    void montar() {
        admin.setAtivo(true);
        locatario.setAtivo(true);

        service = new AdministracaoService();
        ReflectionTestUtils.setField(service, "usuarioRepository", Dobles.repositorio(UsuarioRepository.class)
                .responde("findById", a -> a[0].equals(1L) ? Optional.of(admin)
                        : a[0].equals(2L) ? Optional.of(locatario) : Optional.empty())
                .criar());
    }

    @Test
    void adminDesativaUmLocatario() {
        service.definirAtivoDoUsuario(2L, false, admin);

        assertFalse(locatario.getAtivo());
    }

    @Test
    void adminReativaUmLocatario() {
        locatario.setAtivo(false);

        service.definirAtivoDoUsuario(2L, true, admin);

        assertTrue(locatario.getAtivo());
    }

    @Test
    void adminNaoDesativaASiMesmo() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.definirAtivoDoUsuario(1L, false, admin));

        assertTrue(erro.getMessage().contains("propria conta"));
        assertTrue(admin.getAtivo());
    }

    @Test
    void usuarioInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.definirAtivoDoUsuario(999L, false, admin));
    }
}
