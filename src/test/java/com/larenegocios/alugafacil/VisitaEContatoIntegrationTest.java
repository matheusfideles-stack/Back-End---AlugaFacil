package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class VisitaEContatoIntegrationTest extends IntegracaoBase {

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

    @Autowired
    private UsuarioRepository usuarios;

    private Map<String, Object> locador;
    private Map<String, Object> locatario;
    private String tokenLocador;
    private String tokenLocatario;
    private long imovelId;

    @BeforeEach
    void preparar() throws Exception {
        locador = cadastrar("LOCADOR");
        locatario = cadastrar("LOCATARIO");
        tokenLocador = login(locador);
        tokenLocatario = login(locatario);
        imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        caixa.enviados.clear();
    }

    private Map<String, Object> pedidoDeVisita() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("dataHora", LocalDateTime.now().plusDays(3).withNano(0).toString());
        dados.put("mensagem", "Posso ir depois do trabalho");
        return dados;
    }

    private long agendar() throws Exception {
        return idDaResposta(mockMvc.perform(postJson("/imoveis/" + imovelId + "/visitas", pedidoDeVisita(), tokenLocatario))
                .andExpect(status().isOk()));
    }

    private List<RecuperacaoSenhaIntegrationTest.EmailEnviado> emailsPara(Object endereco) {
        return caixa.enviados.stream().filter(e -> e.para().equals(endereco)).toList();
    }

    // ----- visita

    @Test
    void locatarioPedeVisitaEOProprietarioRecebeOAviso() throws Exception {
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/visitas", pedidoDeVisita(), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLICITADA"))
                .andExpect(jsonPath("$.tituloImovel").value("Apartamento 2 quartos"));

        var email = emailsPara(locador.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("pedido de visita"));
        assertTrue(email.get(0).texto().contains("Posso ir depois do trabalho"));
    }

    @Test
    void dataNoPassadoEhRecusada() throws Exception {
        Map<String, Object> dados = pedidoDeVisita();
        dados.put("dataHora", LocalDateTime.now().minusDays(1).withNano(0).toString());

        mockMvc.perform(postJson("/imoveis/" + imovelId + "/visitas", dados, tokenLocatario))
                .andExpect(status().isBadRequest());
    }

    @Test
    void proprietarioConfirmaEOLocatarioRecebeOAviso() throws Exception {
        long id = agendar();
        caixa.enviados.clear();

        mockMvc.perform(put("/visitas/" + id + "/confirmar").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));

        var email = emailsPara(locatario.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("confirmada"));
    }

    @Test
    void proprietarioRecusaEOLocatarioRecebeOAviso() throws Exception {
        long id = agendar();
        caixa.enviados.clear();

        mockMvc.perform(put("/visitas/" + id + "/recusar").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));

        assertTrue(emailsPara(locatario.get("email")).get(0).assunto().contains("nao confirmada"));
    }

    @Test
    void soOProprietarioDoImovelRespondeEUmaVezSo() throws Exception {
        long id = agendar();

        mockMvc.perform(put("/visitas/" + id + "/confirmar").header("Authorization", "Bearer " + tokenDe("LOCADOR")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/visitas/" + id + "/confirmar").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/visitas/" + id + "/confirmar").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isOk());
        mockMvc.perform(put("/visitas/" + id + "/recusar").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void locatarioCancelaASuaVisitaMasNaoADeOutro() throws Exception {
        long id = agendar();

        mockMvc.perform(put("/visitas/" + id + "/cancelar").header("Authorization", "Bearer " + tokenDe("LOCATARIO")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/visitas/" + id + "/cancelar").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        mockMvc.perform(put("/visitas/" + id + "/cancelar").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void imovelForaDaBuscaNaoRecebePedidoDeVisita() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/imoveis/" + imovelId)
                .header("Authorization", "Bearer " + tokenLocador)).andExpect(status().isNoContent());

        mockMvc.perform(postJson("/imoveis/" + imovelId + "/visitas", pedidoDeVisita(), tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listagensDeVisitaMostramSoParaQuemParticipa() throws Exception {
        long id = agendar();

        mockMvc.perform(get("/visitas/minhas").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content[0].id").value((int) id));
        mockMvc.perform(get("/visitas/recebidas").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(jsonPath("$.content[0].id").value((int) id))
                .andExpect(jsonPath("$.content[0].nomeLocatario").value(locatario.get("nome")));
        mockMvc.perform(get("/visitas/recebidas").header("Authorization", "Bearer " + tokenDe("LOCADOR")))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void permissoesDeVisitaPorPerfil() throws Exception {
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/visitas", pedidoDeVisita(), tokenLocador))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/visitas", pedidoDeVisita()))
                .andExpect(status().isUnauthorized());
    }

    // ----- contato

    @Test
    void whatsappAparecePraLocatarioQuandoOProprietarioTemTelefone() throws Exception {
        var usuario = usuarios.findUsuarioByEmail((String) locador.get("email")).orElseThrow();
        usuario.setTelefone("(34) 97777-6666");
        usuarios.save(usuario);

        mockMvc.perform(get("/imoveis/" + imovelId + "/contato").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("5534977776666"))
                .andExpect(jsonPath("$.nome").isNotEmpty());
    }

    @Test
    void semTelefoneNaoHaWhatsapp() throws Exception {
        mockMvc.perform(get("/imoveis/" + imovelId + "/contato").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
    }

    @Test
    void contatoNaoVazaOEmailNemOTelefoneBruto() throws Exception {
        String corpo = mockMvc.perform(get("/imoveis/" + imovelId + "/contato").header("Authorization", "Bearer " + tokenLocatario))
                .andReturn().getResponse().getContentAsString();

        assertTrue(!corpo.contains("@"), "a resposta nao deve conter o e-mail do proprietario");
    }

    @Test
    void contatoExigeLoginDeLocatario() throws Exception {
        mockMvc.perform(get("/imoveis/" + imovelId + "/contato")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/imoveis/" + imovelId + "/contato").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isForbidden());
    }

    @Test
    void mensagemChegaPorEmailAoProprietarioComOsDadosDeQuemEscreveu() throws Exception {
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/mensagens", Map.of("mensagem", "O imovel aceita animais?"), tokenLocatario))
                .andExpect(status().isNoContent());

        var email = emailsPara(locador.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("mensagem sobre"));
        assertTrue(email.get(0).texto().contains("O imovel aceita animais?"));
        assertTrue(email.get(0).texto().contains((String) locatario.get("email")));
    }

    @Test
    void mensagemCurtaOuVaziaEhRecusada() throws Exception {
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/mensagens", Map.of("mensagem", "oi"), tokenLocatario))
                .andExpect(status().isBadRequest());
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/mensagens", Map.of("mensagem", "   "), tokenLocatario))
                .andExpect(status().isBadRequest());
        assertTrue(caixa.enviados.isEmpty());
    }

    @Test
    void mensagemSoDeLocatarioLogado() throws Exception {
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/mensagens", Map.of("mensagem", "Ola, tudo bem?")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(postJson("/imoveis/" + imovelId + "/mensagens", Map.of("mensagem", "Ola, tudo bem?"), tokenLocador))
                .andExpect(status().isForbidden());
    }
}
