package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.TokenRecuperacaoSenha;
import com.larenegocios.alugafacil.domain.usuario.TokenRecuperacaoSenhaRepository;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.security.GeradorDeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecuperacaoSenhaServiceTest {

    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");

    private final List<TokenRecuperacaoSenha> tokens = new ArrayList<>();
    private final ConfirmacaoEmailServiceTest.EmailFalso email = new ConfirmacaoEmailServiceTest.EmailFalso();
    private Usuario usuario;
    private RecuperacaoSenhaService service;

    @BeforeEach
    void montar() {
        tokens.clear();
        email.enviados.clear();
        email.deVerdade = true;
        email.falha = false;

        usuario = Dobles.usuario(1, Perfil.LOCATARIO);
        usuario.setEmail("maria@teste.com");
        usuario.setSenha("cifrada:antiga123");
        usuario.setEmailConfirmado(false);

        UsuarioRepository usuarios = Dobles.repositorio(UsuarioRepository.class)
                .responde("findUsuarioByEmail", a -> a[0].equals(usuario.getEmail()) ? Optional.of(usuario) : Optional.empty())
                .responde("save", a -> a[0])
                .criar();

        TokenRecuperacaoSenhaRepository repositorio = Dobles.repositorio(TokenRecuperacaoSenhaRepository.class)
                .responde("invalidarPendentes", a -> {
                    tokens.stream().filter(t -> t.getUsadoEm() == null).forEach(t -> t.setUsadoEm((LocalDateTime) a[1]));
                    return null;
                })
                .responde("existsByTokenHash", a -> tokens.stream().anyMatch(t -> t.getTokenHash().equals(a[0])))
                .responde("save", a -> {
                    TokenRecuperacaoSenha token = (TokenRecuperacaoSenha) a[0];
                    if (token.getId() == null) {
                        token.setId(tokens.size() + 1L);
                        tokens.add(token);
                    }
                    return token;
                })
                .responde("findFirstByUsuarioIdAndUsadoEmIsNullOrderByIdDesc", a -> tokens.stream()
                        .filter(t -> t.getUsadoEm() == null)
                        .max(Comparator.comparing(TokenRecuperacaoSenha::getId)))
                .criar();

        service = new RecuperacaoSenhaService();
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(service, "tokenRepository", repositorio);
        ReflectionTestUtils.setField(service, "passwordEncoder", AutenticacaoServiceTest.ENCODER_DE_TESTE);
        ReflectionTestUtils.setField(service, "envioDeEmail", email);
        ReflectionTestUtils.setField(service, "geradorDeToken", new GeradorDeToken());
        ReflectionTestUtils.setField(service, "urlBase", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "validadeMinutos", 30L);
        ReflectionTestUtils.setField(service, "maxTentativas", 3);
        ReflectionTestUtils.setField(service, "exibirLinksDeTeste", false);
    }

    private String codigoDoUltimoEmail() {
        Matcher m = CODIGO.matcher(email.enviados.get(email.enviados.size() - 1)[2]);
        assertTrue(m.find(), "o e-mail deveria trazer um codigo de 6 digitos");
        return m.group(1);
    }

    private String codigoErrado(String certo) {
        return certo.equals("000000") ? "111111" : "000000";
    }

    @Test
    void pedidoParaContaExistenteEnviaOCodigo() {
        service.solicitar("maria@teste.com");

        assertEquals(1, email.enviados.size());
        assertEquals("maria@teste.com", email.enviados.get(0)[0]);
        assertEquals(1, tokens.size());
    }

    @Test
    void pedidoParaEmailDesconhecidoNaoEnviaNadaENaoRevelaIsso() {
        Optional<String> retorno = service.solicitar("ninguem@teste.com");

        assertTrue(email.enviados.isEmpty());
        assertTrue(tokens.isEmpty());
        assertEquals(Optional.empty(), retorno);
    }

    @Test
    void pedidoParaContaDesativadaNaoEnviaNada() {
        usuario.setAtivo(false);

        service.solicitar("maria@teste.com");

        assertTrue(email.enviados.isEmpty());
    }

    @Test
    void emailComMaiusculasEEspacosEhReconhecido() {
        service.solicitar("  MARIA@Teste.com ");

        assertEquals(1, email.enviados.size());
    }

    @Test
    void codigoCorretoTrocaASenhaCifradaEConfirmaOEmail() {
        service.solicitar("maria@teste.com");

        service.redefinir("maria@teste.com", codigoDoUltimoEmail(), "novaSenha99");

        assertEquals("cifrada:novaSenha99", usuario.getSenha());
        assertTrue(usuario.getEmailConfirmado());
        assertNotNull(tokens.get(0).getUsadoEm());
    }

    @Test
    void codigoErradoNaoTrocaASenhaEGastaUmaTentativa() {
        service.solicitar("maria@teste.com");
        String errado = codigoErrado(codigoDoUltimoEmail());

        assertThrows(RegraDeNegocioException.class, () -> service.redefinir("maria@teste.com", errado, "novaSenha99"));

        assertEquals("cifrada:antiga123", usuario.getSenha());
        assertFalse(usuario.getEmailConfirmado());
        assertEquals(1, tokens.get(0).getTentativas());
    }

    @Test
    void depoisDoLimiteDeTentativasOCodigoCertoTambemEhRecusado() {
        service.solicitar("maria@teste.com");
        String certo = codigoDoUltimoEmail();
        String errado = codigoErrado(certo);

        for (int i = 0; i < 3; i++) {
            assertThrows(RegraDeNegocioException.class, () -> service.redefinir("maria@teste.com", errado, "novaSenha99"));
        }
        assertThrows(RegraDeNegocioException.class, () -> service.redefinir("maria@teste.com", certo, "novaSenha99"));

        assertEquals("cifrada:antiga123", usuario.getSenha());
    }

    @Test
    void codigoExpiradoEhRecusado() {
        service.solicitar("maria@teste.com");
        String codigo = codigoDoUltimoEmail();
        tokens.get(0).setExpiraEm(LocalDateTime.now().minusSeconds(1));

        assertThrows(RegraDeNegocioException.class, () -> service.redefinir("maria@teste.com", codigo, "novaSenha99"));

        assertEquals("cifrada:antiga123", usuario.getSenha());
    }

    @Test
    void codigoSoPodeSerUsadoUmaVez() {
        service.solicitar("maria@teste.com");
        String codigo = codigoDoUltimoEmail();
        service.redefinir("maria@teste.com", codigo, "novaSenha99");

        assertThrows(RegraDeNegocioException.class, () -> service.redefinir("maria@teste.com", codigo, "outraSenha88"));

        assertEquals("cifrada:novaSenha99", usuario.getSenha());
    }

    @Test
    void novoPedidoInvalidaOCodigoAnterior() {
        service.solicitar("maria@teste.com");
        String antigo = codigoDoUltimoEmail();
        service.solicitar("maria@teste.com");
        String novo = codigoDoUltimoEmail();

        if (!antigo.equals(novo)) {
            assertThrows(RegraDeNegocioException.class, () -> service.redefinir("maria@teste.com", antigo, "novaSenha99"));
        }
        service.redefinir("maria@teste.com", novo, "novaSenha99");

        assertEquals("cifrada:novaSenha99", usuario.getSenha());
    }

    @Test
    void redefinirParaEmailDesconhecidoDaAMesmaMensagemDeCodigoInvalido() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.redefinir("ninguem@teste.com", "123456", "novaSenha99"));

        assertTrue(erro.getMessage().startsWith("Codigo invalido"));
    }

    @Test
    void falhaNoEnvioNaoQuebraOPedido() {
        email.falha = true;

        service.solicitar("maria@teste.com");

        assertEquals(1, tokens.size());
    }

    @Test
    void codigoSoVoltaNaRespostaNoModoDeTesteSemSmtp() {
        email.deVerdade = true;
        ReflectionTestUtils.setField(service, "exibirLinksDeTeste", true);
        assertEquals(Optional.empty(), service.solicitar("maria@teste.com"));

        email.deVerdade = false;
        assertTrue(service.solicitar("maria@teste.com").isPresent());

        ReflectionTestUtils.setField(service, "exibirLinksDeTeste", false);
        assertEquals(Optional.empty(), service.solicitar("maria@teste.com"));
    }
}
