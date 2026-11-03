package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.dto.DadosCadastroUsuario;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutenticacaoServiceTest {

    // Cifra de mentira, so para o teste enxergar que a senha passou pelo encoder.
    static final PasswordEncoder ENCODER_DE_TESTE = new PasswordEncoder() {
        @Override
        public String encode(CharSequence senha) {
            return "cifrada:" + senha;
        }

        @Override
        public boolean matches(CharSequence senha, String cifrada) {
            return encode(senha).equals(cifrada);
        }
    };

    private final List<Usuario> salvos = new ArrayList<>();
    private boolean jaExiste;
    private String emailConsultado;
    private String cpfConsultado;
    private AutenticacaoService service;

    @BeforeEach
    void montar() {
        salvos.clear();
        jaExiste = false;
        emailConsultado = null;
        cpfConsultado = null;

        UsuarioRepository repositorio = Dobles.repositorio(UsuarioRepository.class)
                .responde("existsByEmailOrCpf", a -> {
                    emailConsultado = (String) a[0];
                    cpfConsultado = (String) a[1];
                    return jaExiste;
                })
                .responde("save", a -> {
                    salvos.add((Usuario) a[0]);
                    return a[0];
                })
                .responde("findByEmail", a -> salvos.stream()
                        .filter(u -> u.getEmail().equals(a[0])).map(u -> (UserDetails) u).findFirst())
                .criar();

        service = new AutenticacaoService();
        ReflectionTestUtils.setField(service, "usuarioRepository", repositorio);
        ReflectionTestUtils.setField(service, "passwordEncoder", ENCODER_DE_TESTE);
    }

    private DadosCadastroUsuario dados(String cpf, Perfil perfil) {
        return new DadosCadastroUsuario("  Maria Souza ", "  Maria@Teste.COM ", cpf, " 81 99999-0000 ", "senha1234", perfil);
    }

    @Test
    void cadastroGuardaEmailMinusculoCpfSoComDigitosENomeSemEspacos() {
        Usuario usuario = service.cadastrar(dados("123.456.789-00", null));

        assertEquals("maria@teste.com", usuario.getEmail());
        assertEquals("12345678900", usuario.getCpf());
        assertEquals("Maria Souza", usuario.getNome());
        assertEquals("81 99999-0000", usuario.getTelefone());
    }

    @Test
    void cadastroGuardaSenhaCifradaENuncaEmTextoPuro() {
        Usuario usuario = service.cadastrar(dados("12345678900", null));

        assertEquals("cifrada:senha1234", usuario.getSenha());
        assertNotEquals("senha1234", usuario.getSenha());
    }

    @Test
    void contaNovaComecaSemEmailConfirmado() {
        Usuario usuario = service.cadastrar(dados("12345678900", null));

        assertFalse(usuario.getEmailConfirmado());
        assertTrue(usuario.getAtivo());
    }

    @Test
    void cadastroPeloSiteSempreViraLocatario() {
        assertEquals(Perfil.LOCATARIO, service.cadastrar(dados("12345678900", null)).getPerfil());
        assertEquals(Perfil.LOCATARIO, service.cadastrar(dados("12345678901", Perfil.LOCATARIO)).getPerfil());
    }

    @Test
    void cadastroPeloSiteNaoAceitaPedirPerfilDeLocadorOuAdmin() {
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(dados("12345678900", Perfil.ADMIN)));
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(dados("12345678900", Perfil.LOCADOR)));
        assertTrue(salvos.isEmpty());
    }

    @Test
    void cadastroDeLocadorFeitoPeloAdminCriaLocador() {
        assertEquals(Perfil.LOCADOR, service.cadastrarLocador(dados("12345678900", null)).getPerfil());
    }

    @Test
    void cpfComMenosDeOnzeDigitosEhRecusado() {
        var erro = assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(dados("123.456.789", null)));

        assertTrue(erro.getMessage().contains("CPF"));
        assertTrue(salvos.isEmpty());
    }

    @Test
    void cpfComLetrasQueDeixamMenosDeOnzeDigitosEhRecusado() {
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(dados("abc12345678", null)));
    }

    @Test
    void emailOuCpfJaCadastradoEhRecusadoComOsDadosNormalizados() {
        jaExiste = true;

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(dados("123.456.789-00", null)));

        assertTrue(erro.getMessage().contains("Ja existe"));
        assertEquals("maria@teste.com", emailConsultado);
        assertEquals("12345678900", cpfConsultado);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void telefoneEmBrancoViraNulo() {
        var dados = new DadosCadastroUsuario("Maria", "maria@teste.com", "12345678900", "   ", "senha1234", null);

        assertNull(service.cadastrar(dados).getTelefone());
    }

    @Test
    void normalizarEmailLidaComNuloEEspacos() {
        assertEquals("", AutenticacaoService.normalizarEmail(null));
        assertEquals("a@b.com", AutenticacaoService.normalizarEmail("  A@B.com "));
    }

    @Test
    void loginEncontraOUsuarioIgnorandoMaiusculas() {
        service.cadastrar(dados("12345678900", null));

        UserDetails encontrado = service.loadUserByUsername("MARIA@teste.com");

        assertEquals("maria@teste.com", encontrado.getUsername());
    }

    @Test
    void loginDeEmailDesconhecidoDaErroDeUsuarioNaoEncontrado() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ninguem@teste.com"));
    }
}
