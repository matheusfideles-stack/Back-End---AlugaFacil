package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.usuario.TokenConfirmacaoEmail;
import com.larenegocios.alugafacil.domain.usuario.TokenConfirmacaoEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class ConfirmacaoEmailIntegrationTest extends IntegracaoBase {

    private static final Pattern CODIGO_NO_EMAIL = Pattern.compile("(?m)^\\s+(\\d{6})\\s*$");

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    @Autowired
    private TokenConfirmacaoEmailRepository tokenRepository;

    @BeforeEach
    void limparCaixa() {
        caixa.enviados.clear();
    }

    private String codigoDoUltimoEmail(String para) {
        var emails = caixa.enviados.stream().filter(e -> e.para().equals(para)).toList();
        assertFalse(emails.isEmpty(), "deveria ter saido um e-mail para " + para);
        Matcher m = CODIGO_NO_EMAIL.matcher(emails.get(emails.size() - 1).texto());
        assertTrue(m.find(), "o e-mail deveria trazer o codigo de verificacao");
        return m.group(1);
    }

    private static String codigoDiferenteDe(String codigo) {
        return "000000".equals(codigo) ? "111111" : "000000";
    }

    private int loginStatus(Map<String, Object> usuario, String senha) throws Exception {
        return mockMvc.perform(postJson("/auth/login", Map.of("email", usuario.get("email"), "senha", senha)))
                .andReturn().getResponse().getStatus();
    }

    private void confirmar(Object email, String codigo, int statusEsperado) throws Exception {
        mockMvc.perform(postJson("/auth/confirmar-email", Map.of("email", email, "codigo", codigo)))
                .andExpect(status().is(statusEsperado));
    }

    @Test
    void cadastroEnviaCodigoDeVerificacaoEContaNasceNaoConfirmada() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String email = (String) usuario.get("email");

        assertEquals(1, caixa.enviados.size());
        assertEquals(email, caixa.enviados.get(0).para());
        assertTrue(caixa.enviados.get(0).assunto().toLowerCase().contains("codigo de verificacao"));
        assertEquals(6, codigoDoUltimoEmail(email).length());
        assertFalse(usuarioRepository.findUsuarioByEmail(email).orElseThrow().getEmailConfirmado());
    }

    @Test
    void naoEntraAntesDeConfirmarMasSenhaErradaContinua401() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();

        mockMvc.perform(postJson("/auth/login", Map.of("email", usuario.get("email"), "senha", SENHA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NAO_CONFIRMADO"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertEquals(401, loginStatus(usuario, "senha-errada"));
    }

    @Test
    void digitarOCodigoLiberaOLogin() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String codigo = codigoDoUltimoEmail((String) usuario.get("email"));

        confirmar(usuario.get("email"), codigo, 200);

        assertEquals(200, loginStatus(usuario, SENHA));
    }

    @Test
    void codigoErradoNaoConfirmaEContinuaSemEntrar() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String certo = codigoDoUltimoEmail((String) usuario.get("email"));

        confirmar(usuario.get("email"), codigoDiferenteDe(certo), 422);

        assertEquals(403, loginStatus(usuario, SENHA));
    }

    @Test
    void codigoDeOutraPessoaNaoConfirmaEstaConta() throws Exception {
        Map<String, Object> primeiro = cadastrarSemConfirmar();
        Map<String, Object> segundo = cadastrarSemConfirmar();
        String codigoDoPrimeiro = codigoDoUltimoEmail((String) primeiro.get("email"));
        String codigoDoSegundo = codigoDoUltimoEmail((String) segundo.get("email"));
        if (codigoDoPrimeiro.equals(codigoDoSegundo)) {
            return;
        }

        confirmar(segundo.get("email"), codigoDoPrimeiro, 422);

        assertEquals(403, loginStatus(segundo, SENHA));
    }

    @Test
    void codigoSoPodeSerUsadoUmaVez() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String codigo = codigoDoUltimoEmail((String) usuario.get("email"));

        confirmar(usuario.get("email"), codigo, 200);
        confirmar(usuario.get("email"), codigo, 422);
    }

    @Test
    void cincoErrosBloqueiamOCodigoMesmoComOCertoDepois() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String certo = codigoDoUltimoEmail((String) usuario.get("email"));
        String errado = codigoDiferenteDe(certo);

        for (int i = 0; i < 5; i++) {
            confirmar(usuario.get("email"), errado, 422);
        }
        confirmar(usuario.get("email"), certo, 422);

        assertEquals(403, loginStatus(usuario, SENHA));
    }

    @Test
    void depoisDoBloqueioUmNovoCodigoFunciona() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String email = (String) usuario.get("email");
        String errado = codigoDiferenteDe(codigoDoUltimoEmail(email));
        for (int i = 0; i < 5; i++) {
            confirmar(email, errado, 422);
        }

        mockMvc.perform(postJson("/auth/reenviar-confirmacao", Map.of("email", email))).andExpect(status().isOk());
        confirmar(email, codigoDoUltimoEmail(email), 200);

        assertEquals(200, loginStatus(usuario, SENHA));
    }

    @Test
    void codigoExpiradoEhRejeitado() throws Exception {
        confirmar("ninguem@teste.com", "123456", 422);

        Map<String, Object> usuario = cadastrarSemConfirmar();
        String email = (String) usuario.get("email");
        String codigo = codigoDoUltimoEmail(email);
        for (TokenConfirmacaoEmail t : tokenRepository.findAll()) {
            if (t.getUsuario().getEmail().equals(email)) {
                t.setExpiraEm(LocalDateTime.now().minusMinutes(1));
                tokenRepository.save(t);
            }
        }

        confirmar(email, codigo, 422);
        assertEquals(403, loginStatus(usuario, SENHA));
    }

    @Test
    void codigoMalFormadoDevolve400() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();

        confirmar(usuario.get("email"), "12ab56", 400);
        confirmar(usuario.get("email"), "12345", 400);
        confirmar(usuario.get("email"), "1234567", 400);
    }

    @Test
    void reenviarGeraNovoCodigoEInvalidaOAnterior() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String email = (String) usuario.get("email");
        String primeiro = codigoDoUltimoEmail(email);

        mockMvc.perform(postJson("/auth/reenviar-confirmacao", Map.of("email", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
        String segundo = codigoDoUltimoEmail(email);

        if (!primeiro.equals(segundo)) {
            confirmar(email, primeiro, 422);
        }
        confirmar(email, segundo, 200);
    }

    @Test
    void codigoNaoFicaGuardadoEmTextoPuroNoBanco() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String codigo = codigoDoUltimoEmail((String) usuario.get("email"));

        boolean achouEmTextoPuro = tokenRepository.findAll().stream()
                .anyMatch(t -> t.getTokenHash().equals(codigo));

        assertFalse(achouEmTextoPuro);
        assertNotEquals(6, tokenRepository.findAll().get(0).getTokenHash().length());
    }

    @Test
    void reenviarNaoRevelaContasNemReenviaParaQuemJaConfirmou() throws Exception {
        mockMvc.perform(postJson("/auth/reenviar-confirmacao", Map.of("email", "ninguem@teste.com")))
                .andExpect(status().isOk());
        Map<String, Object> confirmado = cadastrar("LOCATARIO");
        caixa.enviados.clear();
        mockMvc.perform(postJson("/auth/reenviar-confirmacao", Map.of("email", confirmado.get("email"))))
                .andExpect(status().isOk());

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void redefinirSenhaPeloCodigoTambemConfirmaAConta() throws Exception {
        Map<String, Object> usuario = cadastrarSemConfirmar();
        String email = (String) usuario.get("email");
        caixa.enviados.clear();

        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", email))).andExpect(status().isOk());
        String codigo = codigoDoUltimoEmail(email);
        mockMvc.perform(postJson("/auth/redefinir-senha",
                        Map.of("email", email, "codigo", codigo, "novaSenha", "NovaSenha456!")))
                .andExpect(status().isOk());

        assertEquals(200, loginStatus(usuario, "NovaSenha456!"));
    }

    @Test
    void proprietarioCadastradoPeloAdminTambemPrecisaConfirmar() throws Exception {
        String tokenAdmin = tokenDe("ADMIN");
        Map<String, Object> locador = dadosUsuario("LOCADOR");
        caixa.enviados.clear();

        mockMvc.perform(postJson("/admin/locadores", locador, tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmado").value(false))
                .andExpect(jsonPath("$.codigoDeTeste").doesNotExist());
        assertEquals(403, loginStatus(locador, SENHA));

        confirmar(locador.get("email"), codigoDoUltimoEmail((String) locador.get("email")), 200);
        assertEquals(200, loginStatus(locador, SENHA));
    }

    @Test
    void emailMalFormadoNoReenvioDevolve400() throws Exception {
        mockMvc.perform(postJson("/auth/reenviar-confirmacao", Map.of("email", "invalido")))
                .andExpect(status().isBadRequest());
    }
}
