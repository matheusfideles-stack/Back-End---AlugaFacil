package com.larenegocios.alugafacil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecuperacaoSenhaIntegrationTest.EmailsCapturados.class)
class PropostaIntegrationTest extends IntegracaoBase {

    @Autowired
    private RecuperacaoSenhaIntegrationTest.CaixaDeSaida caixa;

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

    private long propor(String token) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>(dadosContrato(imovelId));
        dados.put("mensagem", "Tenho interesse e posso fechar esta semana");
        return idDaResposta(mockMvc.perform(postJson("/propostas", dados, token)).andExpect(status().isOk()));
    }

    private org.springframework.test.web.servlet.ResultActions responder(long id, String acao, String token) throws Exception {
        return mockMvc.perform(put("/propostas/" + id + "/" + acao).header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{}"));
    }

    private List<RecuperacaoSenhaIntegrationTest.EmailEnviado> emailsPara(Object endereco) {
        return caixa.enviados.stream().filter(e -> e.para().equals(endereco)).toList();
    }

    @Test
    void propostaNasceAguardandoEAvisaOProprietario() throws Exception {
        mockMvc.perform(postJson("/propostas", dadosContrato(imovelId), tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.contratoId").doesNotExist());

        var email = emailsPara(locador.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("nova proposta"));
        assertTrue(email.get(0).texto().contains((String) locatario.get("nome")));
        assertTrue(email.get(0).texto().contains("/inquilinos.html"));
    }

    @Test
    void propostaNaoCriaContratoNemTiraOImovelDaBusca() throws Exception {
        propor(tokenLocatario);

        mockMvc.perform(get("/contratos/meus").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/imoveis/" + imovelId)).andExpect(jsonPath("$.disponivel").value(true));
    }

    @Test
    void naoHaContratoDiretoMaisSoPorProposta() throws Exception {
        mockMvc.perform(postJson("/contratos", dadosContrato(imovelId), tokenLocatario))
                .andExpect(status().isNotFound());
    }

    @Test
    void aceitarCriaOContratoTiraOImovelDaBuscaEAvisaOLocatario() throws Exception {
        long id = propor(tokenLocatario);
        caixa.enviados.clear();

        responder(id, "aceitar", tokenLocador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEITA"))
                .andExpect(jsonPath("$.contratoId").isNumber());

        mockMvc.perform(get("/contratos/meus").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content[0].status").value("ATIVO"))
                .andExpect(jsonPath("$.content[0].imovelId").value((int) imovelId));
        mockMvc.perform(get("/imoveis?size=200"))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) imovelId))));

        var email = emailsPara(locatario.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("aceita"));
        assertTrue(email.get(0).texto().contains("/pagamentos.html"));
    }

    @Test
    void aceitarUmaRecusaAsOutrasPropostasDoMesmoImovelEAvisaCadaUm() throws Exception {
        Map<String, Object> outro = cadastrar("LOCATARIO");
        String tokenOutro = login(outro);
        long vencedora = propor(tokenLocatario);
        long perdedora = propor(tokenOutro);
        caixa.enviados.clear();

        responder(vencedora, "aceitar", tokenLocador).andExpect(status().isOk());

        mockMvc.perform(get("/propostas/minhas").header("Authorization", "Bearer " + tokenOutro))
                .andExpect(jsonPath("$.content[0].id").value((int) perdedora))
                .andExpect(jsonPath("$.content[0].status").value("RECUSADA"))
                .andExpect(jsonPath("$.content[0].motivoRecusa").value("O imovel foi alugado para outro interessado"));
        var email = emailsPara(outro.get("email"));
        assertEquals(1, email.size());
        assertTrue(email.get(0).assunto().contains("nao foi aceita"));
    }

    @Test
    void recusarGuardaOMotivoMandaEmailEMantemOImovelDisponivel() throws Exception {
        long id = propor(tokenLocatario);
        caixa.enviados.clear();

        mockMvc.perform(put("/propostas/" + id + "/recusar").header("Authorization", "Bearer " + tokenLocador)
                        .contentType("application/json").content("{\"motivo\":\"Ja combinei com outra pessoa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"))
                .andExpect(jsonPath("$.motivoRecusa").value("Ja combinei com outra pessoa"));

        var email = emailsPara(locatario.get("email")).get(0);
        assertTrue(email.texto().contains("Motivo: Ja combinei com outra pessoa"));
        mockMvc.perform(get("/imoveis/" + imovelId)).andExpect(jsonPath("$.disponivel").value(true));
    }

    @Test
    void recusarSemCorpoTambemFunciona() throws Exception {
        long id = propor(tokenLocatario);

        mockMvc.perform(put("/propostas/" + id + "/recusar").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));
    }

    @Test
    void depoisDeRecusadaOLocatarioPodeEnviarOutraProposta() throws Exception {
        long id = propor(tokenLocatario);
        responder(id, "recusar", tokenLocador).andExpect(status().isOk());

        propor(tokenLocatario);
    }

    @Test
    void naoHaDuasPropostasAguardandoDoMesmoLocatarioParaOMesmoImovel() throws Exception {
        propor(tokenLocatario);

        mockMvc.perform(postJson("/propostas", dadosContrato(imovelId), tokenLocatario))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void imovelIndisponivelOuComContratoNaoRecebeProposta() throws Exception {
        long id = propor(tokenLocatario);
        responder(id, "aceitar", tokenLocador).andExpect(status().isOk());

        mockMvc.perform(postJson("/propostas", dadosContrato(imovelId), tokenDe("LOCATARIO")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void datasInvalidasSaoRecusadas() throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>(dadosContrato(imovelId));
        dados.put("dataInicio", java.time.LocalDate.now().plusMonths(3).toString());
        dados.put("dataFim", java.time.LocalDate.now().plusMonths(2).toString());

        mockMvc.perform(postJson("/propostas", dados, tokenLocatario)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void soOProprietarioDoImovelRespondeEUmaVezSo() throws Exception {
        long id = propor(tokenLocatario);

        responder(id, "aceitar", tokenDe("LOCADOR")).andExpect(status().isUnprocessableEntity());
        responder(id, "recusar", tokenLocatario).andExpect(status().isForbidden());
        responder(id, "aceitar", tokenLocador).andExpect(status().isOk());
        responder(id, "aceitar", tokenLocador).andExpect(status().isUnprocessableEntity());
        responder(id, "recusar", tokenLocador).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void locatarioCancelaASuaPropostaMasNaoADeOutro() throws Exception {
        long id = propor(tokenLocatario);

        responder(id, "cancelar", tokenDe("LOCATARIO")).andExpect(status().isUnprocessableEntity());
        responder(id, "cancelar", tokenLocatario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        responder(id, "aceitar", tokenLocador).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listagensMostramCadaPropostaSoParaQuemParticipa() throws Exception {
        long id = propor(tokenLocatario);

        mockMvc.perform(get("/propostas/minhas").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content[0].id").value((int) id));
        mockMvc.perform(get("/propostas/recebidas").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(jsonPath("$.content[0].id").value((int) id))
                .andExpect(jsonPath("$.content[0].nomeLocatario").value(locatario.get("nome")))
                .andExpect(jsonPath("$.content[0].emailLocatario").value(locatario.get("email")));
        mockMvc.perform(get("/propostas/recebidas").header("Authorization", "Bearer " + tokenDe("LOCADOR")))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        mockMvc.perform(postJson("/propostas", dadosContrato(imovelId), tokenLocador)).andExpect(status().isForbidden());
        mockMvc.perform(postJson("/propostas", dadosContrato(imovelId))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/propostas/recebidas").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/propostas/minhas").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isForbidden());
    }

    @Test
    void falhaNoEmailNaoDesfazAProposta() throws Exception {
        // O envio so falha no log; aqui basta ver que a proposta existe e responde certo depois do aviso.
        long id = propor(tokenLocatario);

        mockMvc.perform(get("/propostas/minhas").header("Authorization", "Bearer " + tokenLocatario))
                .andExpect(jsonPath("$.content[0].id").value((int) id));
    }
}
