package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.usuario.TokenRecuperacaoSenha;
import com.larenegocios.alugafacil.domain.usuario.TokenRecuperacaoSenhaRepository;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class RecuperacaoSenhaIntegrationTest extends IntegracaoBase {

    private static final Pattern CODIGO_NO_EMAIL = Pattern.compile("(?m)^\\s+(\\d{6})\\s*$");

    record EmailEnviado(String para, String assunto, String texto) {
    }

    static class CaixaDeSaida extends EnvioDeEmail {

        final List<EmailEnviado> enviados = new CopyOnWriteArrayList<>();

        CaixaDeSaida() {
            super(null, "teste@alugafacil.local");
        }

        @Override
        public boolean enviaDeVerdade() {
            return false;
        }

        @Override
        public void enviar(String para, String assunto, String texto) {
            enviados.add(new EmailEnviado(para, assunto, texto));
        }
    }

    @TestConfiguration
    static class EmailsCapturados {

        @Bean
        @Primary
        CaixaDeSaida caixaDeSaida() {
            return new CaixaDeSaida();
        }
    }

    @Autowired
    private CaixaDeSaida caixa;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @BeforeEach
    void limparCaixa() {
        caixa.enviados.clear();
    }

    private String pedirCodigo(String email) throws Exception {
        int antes = caixa.enviados.size();
        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", email)))
                .andExpect(status().isOk());

        assertEquals(antes + 1, caixa.enviados.size(), "deveria ter saido exatamente um e-mail novo");
        EmailEnviado ultimo = caixa.enviados.get(caixa.enviados.size() - 1);
        assertEquals(email, ultimo.para());

        Matcher m = CODIGO_NO_EMAIL.matcher(ultimo.texto());
        assertTrue(m.find(), "o e-mail deveria trazer o codigo de redefinicao");
        return m.group(1);
    }

    private static String codigoDiferenteDe(String codigo) {
        return "000000".equals(codigo) ? "111111" : "000000";
    }

    private void redefinir(String email, String codigo, String novaSenha, int statusEsperado) throws Exception {
        mockMvc.perform(postJson("/auth/redefinir-senha", Map.of("email", email, "codigo", codigo, "novaSenha", novaSenha)))
                .andExpect(status().is(statusEsperado));
    }

    private int loginStatus(Map<String, Object> usuario, String senha) throws Exception {
        return mockMvc.perform(postJson("/auth/login", Map.of("email", usuario.get("email"), "senha", senha)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void fluxoCompletoTrocaASenhaEBloqueiaAAntiga() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");

        redefinir(email, pedirCodigo(email), "NovaSenha456!", 200);

        assertEquals(200, loginStatus(usuario, "NovaSenha456!"));
        assertEquals(401, loginStatus(usuario, SENHA));
    }

    @Test
    void novaSenhaTambemFicaComHashBcrypt() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCADOR");
        String email = (String) usuario.get("email");

        redefinir(email, pedirCodigo(email), "OutraSenha789!", 200);

        String armazenada = usuarioRepository.findUsuarioByEmail(email).orElseThrow().getSenha();
        assertTrue(armazenada.startsWith("$2"));
        assertFalse(armazenada.contains("OutraSenha789!"));
    }

    @Test
    void codigoSoPodeSerUsadoUmaVez() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        String codigo = pedirCodigo(email);

        redefinir(email, codigo, "NovaSenha456!", 200);
        redefinir(email, codigo, "OutraSenha789!", 422);
    }

    @Test
    void novoPedidoInvalidaOCodigoAnterior() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");

        String primeiro = pedirCodigo(email);
        String segundo = pedirCodigo(email);

        if (!primeiro.equals(segundo)) {
            redefinir(email, primeiro, "NovaSenha456!", 422);
        }
        redefinir(email, segundo, "NovaSenha456!", 200);
    }

    @Test
    void codigoExpiradoERejeitado() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        String codigo = pedirCodigo(email);

        for (TokenRecuperacaoSenha t : tokenRepository.findAll()) {
            if (t.getUsuario().getEmail().equals(email)) {
                t.setExpiraEm(LocalDateTime.now().minusMinutes(1));
                tokenRepository.save(t);
            }
        }

        redefinir(email, codigo, "NovaSenha456!", 422);
        assertEquals(200, loginStatus(usuario, SENHA));
    }

    @Test
    void codigoErradoERejeitadoESenhaNaoMuda() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        String certo = pedirCodigo(email);

        redefinir(email, codigoDiferenteDe(certo), "NovaSenha456!", 422);

        assertEquals(200, loginStatus(usuario, SENHA));
    }

    @Test
    void codigoParaEmailQueNaoPediuERejeitado() throws Exception {
        redefinir("ninguem@teste.com", "123456", "NovaSenha456!", 422);
    }

    @Test
    void codigoDeOutraContaNaoRedefineEstaSenha() throws Exception {
        Map<String, Object> a = cadastrar("LOCATARIO");
        Map<String, Object> b = cadastrar("LOCATARIO");
        String emailA = (String) a.get("email");
        String emailB = (String) b.get("email");
        String codigoA = pedirCodigo(emailA);
        String codigoB = pedirCodigo(emailB);
        if (codigoA.equals(codigoB)) {
            return;
        }

        redefinir(emailB, codigoA, "NovaSenha456!", 422);

        assertEquals(200, loginStatus(b, SENHA));
    }

    @Test
    void cincoErrosBloqueiamOCodigoMesmoComOCertoDepois() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        String certo = pedirCodigo(email);
        String errado = codigoDiferenteDe(certo);

        for (int i = 0; i < 5; i++) {
            redefinir(email, errado, "NovaSenha456!", 422);
        }
        redefinir(email, certo, "NovaSenha456!", 422);

        assertEquals(200, loginStatus(usuario, SENHA));
        redefinir(email, pedirCodigo(email), "NovaSenha456!", 200);
    }

    @Test
    void senhaFracaNaRedefinicaoDevolve400() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        String codigo = pedirCodigo(email);

        mockMvc.perform(postJson("/auth/redefinir-senha", Map.of("email", email, "codigo", codigo, "novaSenha", "curta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[?(@.campo == 'novaSenha')]").exists());
    }

    @Test
    void codigoMalFormadoDevolve400() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        pedirCodigo(email);

        redefinir(email, "abc123", "NovaSenha456!", 400);
        redefinir(email, "12345", "NovaSenha456!", 400);
    }

    @Test
    void emailInexistenteResponde200SemEnviarNada() throws Exception {
        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", "ninguem-aqui@teste.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void porPadraoARespostaNuncaTrazOCodigo() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");

        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", usuario.get("email"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoDeTeste").doesNotExist());
    }

    @Test
    void emailMalFormadoDevolve400() throws Exception {
        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", "isso-nao-e-email")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codigoNaoFicaGuardadoEmTextoPuroNoBanco() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String codigo = pedirCodigo((String) usuario.get("email"));

        boolean achouEmTextoPuro = tokenRepository.findAll().stream()
                .anyMatch(t -> t.getTokenHash().equals(codigo));

        assertFalse(achouEmTextoPuro);
    }

    @Test
    void emailEmMaiusculasTambemRecebeOCodigo() throws Exception {
        Map<String, Object> usuario = cadastrar("LOCATARIO");
        String email = (String) usuario.get("email");
        caixa.enviados.clear();

        mockMvc.perform(postJson("/auth/esqueci-senha", Map.of("email", email.toUpperCase())))
                .andExpect(status().isOk());

        assertEquals(1, caixa.enviados.size());
        assertEquals(email, caixa.enviados.get(0).para());
    }
}
