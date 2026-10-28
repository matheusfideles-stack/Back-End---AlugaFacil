package com.larenegocios.alugafacil;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FotoImovelIntegrationTest extends IntegracaoBase {

    private long imovelId;
    private String tokenLocador;

    @BeforeEach
    void criarImovel() throws Exception {
        tokenLocador = tokenDe("LOCADOR");
        imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
    }

    private static byte[] png(int variacao) {
        byte[] bytes = new byte[64];
        byte[] assinatura = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(assinatura, 0, bytes, 0, assinatura.length);
        bytes[63] = (byte) variacao;
        return bytes;
    }

    private static byte[] jpg() {
        byte[] bytes = new byte[64];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private static byte[] webp() {
        byte[] bytes = new byte[64];
        System.arraycopy("RIFF".getBytes(), 0, bytes, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, bytes, 8, 4);
        return bytes;
    }

    private static MockMultipartFile foto(String nome, byte[] conteudo) {
        return new MockMultipartFile("fotos", nome, "image/png", conteudo);
    }

    private ResultActions enviar(String token, MockMultipartFile... arquivos) throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart("/imoveis/" + imovelId + "/fotos");
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(pedido);
    }

    private JsonNode listar() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/imoveis/" + imovelId + "/fotos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void proprietarioEnviaVariasFotosEAPrimeiraViraCapa() throws Exception {
        enviar(tokenLocador, foto("sala.png", png(1)), foto("quarto.jpg", jpg()), foto("fachada.webp", webp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].capa").value(true))
                .andExpect(jsonPath("$[1].capa").value(false))
                .andExpect(jsonPath("$[0].url").value(containsString("/imoveis/" + imovelId + "/fotos/")));
    }

    @Test
    void listaDeFotosEPublicaEAFotoSeBaixaSemLogin() throws Exception {
        byte[] original = png(7);
        enviar(tokenLocador, foto("a.png", original)).andExpect(status().isOk());

        JsonNode fotos = listar();
        assertEquals(1, fotos.size());
        MvcResult resposta = mockMvc.perform(get(fotos.get(0).get("url").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andReturn();
        assertArrayEquals(original, resposta.getResponse().getContentAsByteArray());
    }

    @Test
    void tipoVemDoConteudoENaoDoNomeDoArquivo() throws Exception {
        enviar(tokenLocador, new MockMultipartFile("fotos", "imagem.png", "image/png", jpg()))
                .andExpect(status().isOk());

        mockMvc.perform(get(listar().get(0).get("url").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void nomeDoArquivoEnviadoNuncaChegaAoDiscoNemAResposta() throws Exception {
        enviar(tokenLocador, foto("../../../etc/passwd.png", png(2))).andExpect(status().isOk());

        String url = listar().get(0).get("url").asText();
        assertFalse(url.contains("passwd"));
        assertFalse(url.contains(".."));
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void arquivoQueNaoEImagemEhRecusado() throws Exception {
        enviar(tokenLocador, foto("script.png", "<script>alert(1)</script>".getBytes()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Formato nao suportado")));
        enviar(tokenLocador, new MockMultipartFile("fotos", "vetor.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(0, listar().size());
    }

    @Test
    void umArquivoInvalidoNoLoteCancelaOLoteTodo() throws Exception {
        enviar(tokenLocador, foto("boa.png", png(3)), foto("ruim.png", "texto".getBytes()))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(0, listar().size());
    }

    @Test
    void fotoMaiorQueCincoMegasEhRecusada() throws Exception {
        byte[] grande = Arrays.copyOf(png(4), 5 * 1024 * 1024 + 1);

        enviar(tokenLocador, foto("grande.png", grande))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("5 MB")));
    }

    @Test
    void limiteDeDezFotosPorImovel() throws Exception {
        MockMultipartFile[] dez = new MockMultipartFile[10];
        for (int i = 0; i < dez.length; i++) {
            dez[i] = foto("f" + i + ".png", png(i));
        }
        enviar(tokenLocador, dez).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));

        enviar(tokenLocador, foto("onze.png", png(99)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("10 fotos")));
        assertEquals(10, listar().size());
    }

    @Test
    void envioSemNenhumArquivoEhRecusado() throws Exception {
        mockMvc.perform(multipart("/imoveis/" + imovelId + "/fotos").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isUnprocessableEntity());
        enviar(tokenLocador, foto("vazio.png", new byte[0])).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void soOProprietarioOuAdminMexeNasFotos() throws Exception {
        enviar(null).andExpect(status().isUnauthorized());
        enviar(tokenDe("LOCATARIO"), foto("a.png", png(5))).andExpect(status().isForbidden());
        enviar(tokenDe("LOCADOR"), foto("a.png", png(5))).andExpect(status().isUnprocessableEntity());
        enviar(login(cadastrarAdmin()), foto("a.png", png(5))).andExpect(status().isOk());

        assertEquals(1, listar().size());
    }

    @Test
    void capaRedirecionaParaAFotoPrincipalEDevolve404SemFotos() throws Exception {
        mockMvc.perform(get("/imoveis/" + imovelId + "/capa")).andExpect(status().isNotFound());

        enviar(tokenLocador, foto("a.png", png(1)), foto("b.png", png(2))).andExpect(status().isOk());
        String primeira = listar().get(0).get("url").asText();

        mockMvc.perform(get("/imoveis/" + imovelId + "/capa"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", primeira));
    }

    @Test
    void proprietarioTrocaAFotoDeCapa() throws Exception {
        enviar(tokenLocador, foto("a.png", png(1)), foto("b.png", png(2)), foto("c.png", png(3)))
                .andExpect(status().isOk());
        JsonNode antes = listar();
        long terceira = antes.get(2).get("id").asLong();

        mockMvc.perform(put("/imoveis/" + imovelId + "/fotos/" + terceira + "/capa")
                        .header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isNoContent());

        JsonNode depois = listar();
        assertEquals(terceira, depois.get(0).get("id").asLong());
        assertTrue(depois.get(0).get("capa").asBoolean());
        assertEquals(3, depois.size());
        mockMvc.perform(get("/imoveis/" + imovelId + "/capa"))
                .andExpect(header().string("Location", depois.get(0).get("url").asText()));
    }

    @Test
    void trocarCapaDeOutroProprietarioEhRecusado() throws Exception {
        enviar(tokenLocador, foto("a.png", png(1)), foto("b.png", png(2))).andExpect(status().isOk());
        long segunda = listar().get(1).get("id").asLong();

        mockMvc.perform(put("/imoveis/" + imovelId + "/fotos/" + segunda + "/capa")
                        .header("Authorization", "Bearer " + tokenDe("LOCADOR")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/imoveis/" + imovelId + "/fotos/" + segunda + "/capa"))
                .andExpect(status().isUnauthorized());
        assertEquals(listar().get(1).get("id").asLong(), segunda);
    }

    @Test
    void proprietarioApagaUmaFotoEOArquivoSome() throws Exception {
        enviar(tokenLocador, foto("a.png", png(1)), foto("b.png", png(2))).andExpect(status().isOk());
        JsonNode fotos = listar();
        long id = fotos.get(0).get("id").asLong();
        String url = fotos.get(0).get("url").asText();

        mockMvc.perform(delete("/imoveis/" + imovelId + "/fotos/" + id).header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isNoContent());

        assertEquals(1, listar().size());
        assertTrue(listar().get(0).get("capa").asBoolean());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void apagarFotoDeOutroProprietarioOuInexistenteEhRecusado() throws Exception {
        enviar(tokenLocador, foto("a.png", png(1))).andExpect(status().isOk());
        long id = listar().get(0).get("id").asLong();

        mockMvc.perform(delete("/imoveis/" + imovelId + "/fotos/" + id).header("Authorization", "Bearer " + tokenDe("LOCADOR")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(delete("/imoveis/" + imovelId + "/fotos/" + id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/imoveis/" + imovelId + "/fotos/999999").header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isNotFound());
        assertEquals(1, listar().size());
    }

    @Test
    void fotoNaoEAcessivelPeloIdDeOutroImovel() throws Exception {
        enviar(tokenLocador, foto("a.png", png(1))).andExpect(status().isOk());
        long idFoto = listar().get(0).get("id").asLong();
        long outroImovel = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenDe("LOCADOR"))));

        mockMvc.perform(get("/imoveis/" + outroImovel + "/fotos/" + idFoto + "/arquivo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void imovelInexistenteDevolve404() throws Exception {
        mockMvc.perform(get("/imoveis/999999/fotos")).andExpect(status().isNotFound());
        mockMvc.perform(multipart("/imoveis/999999/fotos").file(foto("a.png", png(1)))
                        .header("Authorization", "Bearer " + tokenLocador))
                .andExpect(status().isNotFound());
    }
}
