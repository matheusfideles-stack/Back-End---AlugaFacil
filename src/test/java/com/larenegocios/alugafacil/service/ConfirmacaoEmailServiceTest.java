package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.TokenConfirmacaoEmail;
import com.larenegocios.alugafacil.domain.usuario.TokenConfirmacaoEmailRepository;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
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

class ConfirmacaoEmailServiceTest {

    static class EmailFalso extends EnvioDeEmail {

        final List<String[]> enviados = new ArrayList<>();
        boolean deVerdade = true;
        boolean falha = false;

        EmailFalso() {
            super(null, "teste@alugafacil.local");
        }

        @Override
        public boolean enviaDeVerdade() {
            return deVerdade;
        }

        @Override
        public void enviar(String para, String assunto, String texto) {
            if (falha) {
                throw new IllegalStateException("servidor fora do ar");
            }
            enviados.add(new String[]{para, assunto, texto});
        }
    }

    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");

    private final GeradorDeToken gerador = new GeradorDeToken();
    private final List<TokenConfirmacaoEmail> tokens = new ArrayList<>();
    private final EmailFalso email = new EmailFalso();
    private Usuario usuario;
    private ConfirmacaoEmailService service;

    @BeforeEach
    void montar() {
        tokens.clear();
        email.enviados.clear();
        email.deVerdade = true;
        email.falha = false;

        usuario = Dobles.usuario(1, Perfil.LOCATARIO);
        usuario.setEmail("maria@teste.com");
        usuario.setEmailConfirmado(false);

        UsuarioRepository usuarios = Dobles.repositorio(UsuarioRepository.class)
                .responde("findUsuarioByEmail", a -> a[0].equals(usuario.getEmail()) ? Optional.of(usuario) : Optional.empty())
                .responde("save", a -> a[0])
                .criar();

        TokenConfirmacaoEmailRepository repositorio = Dobles.repositorio(TokenConfirmacaoEmailRepository.class)
                .responde("invalidarPendentes", a -> {
                    tokens.stream().filter(t -> t.getUsadoEm() == null).forEach(t -> t.setUsadoEm((LocalDateTime) a[1]));
                    return null;
                })
                .responde("existsByTokenHash", a -> tokens.stream().anyMatch(t -> t.getTokenHash().equals(a[0])))
                .responde("save", a -> {
                    TokenConfirmacaoEmail token = (TokenConfirmacaoEmail) a[0];
                    if (token.getId() == null) {
                        token.setId(tokens.size() + 1L);
                        tokens.add(token);
                    }
                    return token;
                })
                .responde("findFirstByUsuarioIdAndUsadoEmIsNullOrderByIdDesc", a -> tokens.stream()
                        .filter(t -> t.getUsadoEm() == null)
                        .max(Comparator.comparing(TokenConfirmacaoEmail::getId)))
                .criar();

        service = new ConfirmacaoEmailService();
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(service, "tokenRepository", repositorio);
        ReflectionTestUtils.setField(service, "envioDeEmail", email);
        ReflectionTestUtils.setField(service, "geradorDeToken", gerador);
        ReflectionTestUtils.setField(service, "urlBase", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "validadeMinutos", 60L);
        ReflectionTestUtils.setField(service, "maxTentativas", 3);
        ReflectionTestUtils.setField(service, "exibirLinksDeTeste", false);
    }

    private String codigoDoUltimoEmail() {
        Matcher m = CODIGO.matcher(email.enviados.get(email.enviados.size() - 1)[2]);
        assertTrue(m.find(), "o e-mail deveria trazer um codigo de 6 digitos");
        return m.group(1);
    }

    @Test
    void enviaOCodigoParaOEmailDaPessoa() {
        service.enviarConfirmacao(usuario);

        assertEquals(1, email.enviados.size());
        assertEquals("maria@teste.com", email.enviados.get(0)[0]);
        assertEquals(6, codigoDoUltimoEmail().length());
    }

    @Test
    void guardaSoOHashDoCodigoNuncaOCodigo() {
        service.enviarConfirmacao(usuario);

        String codigo = codigoDoUltimoEmail();
        assertNotNull(tokens.get(0).getTokenHash());
        assertFalse(tokens.get(0).getTokenHash().contains(codigo));
        assertEquals(gerador.hash(1L, codigo), tokens.get(0).getTokenHash());
    }

    @Test
    void codigoCorretoConfirmaOEmailEGastaOCodigo() {
        service.enviarConfirmacao(usuario);

        service.confirmar("maria@teste.com", codigoDoUltimoEmail());

        assertTrue(usuario.getEmailConfirmado());
        assertNotNull(tokens.get(0).getUsadoEm());
    }

    @Test
    void emailInformadoComMaiusculasEEspacosTambemConfirma() {
        service.enviarConfirmacao(usuario);

        service.confirmar("  MARIA@teste.com ", codigoDoUltimoEmail());

        assertTrue(usuario.getEmailConfirmado());
    }

    @Test
    void codigoErradoNaoConfirmaEGastaUmaTentativa() {
        service.enviarConfirmacao(usuario);
        String certo = codigoDoUltimoEmail();
        String errado = certo.equals("000000") ? "111111" : "000000";

        assertThrows(RegraDeNegocioException.class, () -> service.confirmar("maria@teste.com", errado));

        assertFalse(usuario.getEmailConfirmado());
        assertEquals(1, tokens.get(0).getTentativas());
    }

    @Test
    void depoisDoLimiteDeTentativasATentativaCertaTambemEhRecusada() {
        service.enviarConfirmacao(usuario);
        String certo = codigoDoUltimoEmail();
        String errado = certo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 3; i++) {
            assertThrows(RegraDeNegocioException.class, () -> service.confirmar("maria@teste.com", errado));
        }
        assertThrows(RegraDeNegocioException.class, () -> service.confirmar("maria@teste.com", certo));

        assertFalse(usuario.getEmailConfirmado());
    }

    @Test
    void codigoExpiradoEhRecusado() {
        service.enviarConfirmacao(usuario);
        String codigo = codigoDoUltimoEmail();
        tokens.get(0).setExpiraEm(LocalDateTime.now().minusMinutes(1));

        assertThrows(RegraDeNegocioException.class, () -> service.confirmar("maria@teste.com", codigo));

        assertFalse(usuario.getEmailConfirmado());
    }

    @Test
    void pedirNovoCodigoInvalidaOAnterior() {
        service.enviarConfirmacao(usuario);
        String antigo = codigoDoUltimoEmail();
        service.enviarConfirmacao(usuario);
        String novo = codigoDoUltimoEmail();

        if (!antigo.equals(novo)) {
            assertThrows(RegraDeNegocioException.class, () -> service.confirmar("maria@teste.com", antigo));
        }
        service.confirmar("maria@teste.com", novo);

        assertTrue(usuario.getEmailConfirmado());
    }

    @Test
    void codigoNaoPodeSerUsadoDuasVezes() {
        service.enviarConfirmacao(usuario);
        String codigo = codigoDoUltimoEmail();
        service.confirmar("maria@teste.com", codigo);

        assertThrows(RegraDeNegocioException.class, () -> service.confirmar("maria@teste.com", codigo));
    }

    @Test
    void emailDesconhecidoRecebeAMesmaMensagemDeCodigoInvalido() {
        var erro = assertThrows(RegraDeNegocioException.class, () -> service.confirmar("ninguem@teste.com", "123456"));

        assertTrue(erro.getMessage().startsWith("Codigo invalido"));
    }

    @Test
    void falhaNoEnvioNaoImpedeOCadastro() {
        email.falha = true;

        service.enviarConfirmacao(usuario);

        assertEquals(1, tokens.size());
    }

    @Test
    void codigoSoVoltaParaATelaNoModoDeTesteSemSmtp() {
        email.deVerdade = false;
        assertEquals(Optional.empty(), service.enviarConfirmacao(usuario));

        ReflectionTestUtils.setField(service, "exibirLinksDeTeste", true);
        Optional<String> exibido = service.enviarConfirmacao(usuario);
        assertTrue(exibido.isPresent());

        email.deVerdade = true;
        assertEquals(Optional.empty(), service.enviarConfirmacao(usuario));
    }

    @Test
    void reenviarSoMandaParaContaExistenteAindaNaoConfirmada() {
        service.reenviar("maria@teste.com");
        assertEquals(1, email.enviados.size());

        service.reenviar("ninguem@teste.com");
        assertEquals(1, email.enviados.size());

        usuario.setEmailConfirmado(true);
        service.reenviar("maria@teste.com");
        assertEquals(1, email.enviados.size());
    }

    @Test
    void reenviarNaoMandaParaContaDesativada() {
        usuario.setAtivo(false);

        service.reenviar("maria@teste.com");

        assertTrue(email.enviados.isEmpty());
    }
}
