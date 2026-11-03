package com.larenegocios.alugafacil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracaoBase {

    private static final AtomicLong SEQUENCIA = new AtomicLong(System.nanoTime() % 1_000_000);

    protected static final String SENHA = "Senha123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String json(Object objeto) throws Exception {
        return objectMapper.writeValueAsString(objeto);
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object corpo) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json(corpo));
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object corpo, String token) throws Exception {
        return postJson(url, corpo).header("Authorization", "Bearer " + token);
    }

    protected Map<String, Object> dadosUsuario(String perfil) {
        long n = SEQUENCIA.incrementAndGet();
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", "Fulano Teste " + n);
        dados.put("email", "usuario" + n + "@teste.com");
        dados.put("cpf", String.format("%011d", n));
        dados.put("telefone", "11999999999");
        dados.put("senha", SENHA);
        dados.put("perfil", perfil);
        return dados;
    }

    protected Map<String, Object> cadastrar(String perfil) throws Exception {
        if (!"LOCATARIO".equals(perfil)) {
            return criarNoBanco(perfil);
        }
        Map<String, Object> dados = cadastrarSemConfirmar();
        confirmarNoBanco((String) dados.get("email"));
        return dados;
    }

    protected Map<String, Object> cadastrarSemConfirmar() throws Exception {
        Map<String, Object> dados = dadosUsuario("LOCATARIO");
        mockMvc.perform(postJson("/auth/registro", dados)).andReturn();
        return dados;
    }

    protected void confirmarNoBanco(String email) {
        Usuario usuario = usuarioRepository.findUsuarioByEmail(email).orElseThrow();
        usuario.setEmailConfirmado(true);
        usuarioRepository.save(usuario);
    }

    protected String login(Map<String, Object> usuario) throws Exception {
        String resposta = mockMvc.perform(postJson("/auth/login",
                        Map.of("email", usuario.get("email"), "senha", usuario.get("senha"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("token").asText();
    }

    protected String tokenDe(String perfil) throws Exception {
        return login(cadastrar(perfil));
    }

    protected Map<String, Object> cadastrarAdmin() {
        return criarNoBanco("ADMIN");
    }

    protected Map<String, Object> criarNoBanco(String perfil) {
        Map<String, Object> dados = dadosUsuario(perfil);
        Usuario usuario = new Usuario();
        usuario.setNome((String) dados.get("nome"));
        usuario.setEmail((String) dados.get("email"));
        usuario.setCpf((String) dados.get("cpf"));
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setPerfil(Perfil.valueOf(perfil));
        usuarioRepository.save(usuario);
        return dados;
    }

    protected Map<String, Object> dadosImovel() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("titulo", "Apartamento 2 quartos");
        dados.put("descricao", "Apartamento confortavel no centro");
        dados.put("tipo", "APARTAMENTO");
        dados.put("logradouro", "Rua das Flores");
        dados.put("numero", "123");
        dados.put("complemento", "Apto 101");
        dados.put("bairro", "Centro");
        dados.put("cidade", "Sao Paulo");
        dados.put("uf", "sp");
        dados.put("cep", "01310-100");
        dados.put("valorAluguel", 1500.00);
        dados.put("valorCondominio", 300.00);
        return dados;
    }

    protected long idDaResposta(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    // O contrato so nasce quando o proprietario aceita uma proposta: devolve o id do contrato criado.
    protected long contratar(long imovelId, String tokenLocatario, String tokenLocador) throws Exception {
        return contratar(dadosContrato(imovelId), tokenLocatario, tokenLocador);
    }

    protected long contratar(Map<String, Object> proposta, String tokenLocatario, String tokenLocador) throws Exception {
        long propostaId = idDaResposta(mockMvc.perform(postJson("/propostas", proposta, tokenLocatario))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()));
        String resposta = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/propostas/" + propostaId + "/aceitar").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("contratoId").asLong();
    }

    protected Map<String, Object> dadosContrato(long imovelId) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("imovelId", imovelId);
        dados.put("dataInicio", LocalDate.now().toString());
        dados.put("dataFim", LocalDate.now().plusYears(1).toString());
        dados.put("diaVencimento", 10);
        return dados;
    }
}
