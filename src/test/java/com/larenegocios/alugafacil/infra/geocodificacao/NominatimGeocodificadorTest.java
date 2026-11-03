package com.larenegocios.alugafacil.infra.geocodificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// O OpenStreetMap de mentira e um servidor HTTP local: da para ver exatamente o que o sistema pergunta e responder
// com o que o servico real responde, sem internet e sem gastar consultas do servico publico.
class NominatimGeocodificadorTest {

    private HttpServer servidor;
    private final List<String> consultas = new ArrayList<>();
    private final Queue<String> respostas = new LinkedList<>();
    private int statusDaResposta;
    private String agenteRecebido;

    @BeforeEach
    void subirServidor() throws IOException {
        statusDaResposta = 200;
        consultas.clear();
        respostas.clear();
        agenteRecebido = null;

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/search", troca -> {
            String consulta = troca.getRequestURI().getRawQuery();
            for (String parte : consulta.split("&")) {
                if (parte.startsWith("q=")) {
                    consultas.add(URLDecoder.decode(parte.substring(2), StandardCharsets.UTF_8));
                }
            }
            agenteRecebido = troca.getRequestHeaders().getFirst("User-Agent");
            String corpo = respostas.isEmpty() ? "[]" : respostas.poll();
            byte[] saida = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(statusDaResposta, saida.length);
            troca.getResponseBody().write(saida);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private NominatimGeocodificador geocodificador(boolean ativa) {
        return new NominatimGeocodificador(new ObjectMapper(), ativa,
                "http://127.0.0.1:" + servidor.getAddress().getPort(), "AlugaFacil-teste/1.0");
    }

    private Optional<Coordenadas> buscar(NominatimGeocodificador g) {
        return g.localizar("Rua Major Gote", "10", "Centro", "Patos de Minas", "MG");
    }

    @Test
    void desligadoNaoConsultaNada() {
        assertEquals(Optional.empty(), buscar(geocodificador(false)));

        assertTrue(consultas.isEmpty());
    }

    @Test
    void ruaEncontradaDevolveALatitudeELongitude() {
        respostas.add("[{\"lat\":\"-18.5919245\",\"lon\":\"-46.5149590\"}]");

        Optional<Coordenadas> resultado = buscar(geocodificador(true));

        assertEquals(new Coordenadas(-18.5919245, -46.5149590), resultado.orElseThrow());
        assertEquals(1, consultas.size());
    }

    @Test
    void aConsultaTrazRuaNumeroBairroCidadeEstadoEPais() {
        respostas.add("[{\"lat\":\"-18.59\",\"lon\":\"-46.51\"}]");

        buscar(geocodificador(true));

        assertEquals("Rua Major Gote 10, Centro, Patos de Minas, MG, Brasil", consultas.get(0));
    }

    @Test
    void identificaAAplicacaoComoPedeAPoliticaDoOpenStreetMap() {
        respostas.add("[{\"lat\":\"-18.59\",\"lon\":\"-46.51\"}]");

        buscar(geocodificador(true));

        assertEquals("AlugaFacil-teste/1.0", agenteRecebido);
    }

    @Test
    void ruaDesconhecidaCaiParaOBairro() {
        respostas.add("[]");
        respostas.add("[{\"lat\":\"-18.60\",\"lon\":\"-46.52\"}]");

        Optional<Coordenadas> resultado = buscar(geocodificador(true));

        assertEquals(new Coordenadas(-18.60, -46.52), resultado.orElseThrow());
        assertEquals(2, consultas.size());
        assertEquals("Centro, Patos de Minas, MG, Brasil", consultas.get(1));
    }

    @Test
    void nadaEncontradoDevolveVazio() {
        respostas.add("[]");
        respostas.add("[]");

        assertEquals(Optional.empty(), buscar(geocodificador(true)));
    }

    @Test
    void erroDoServidorNaoViraExcecao() {
        statusDaResposta = 500;
        respostas.add("erro");

        assertEquals(Optional.empty(), buscar(geocodificador(true)));
    }

    @Test
    void servidorForaDoArNaoViraExcecao() {
        NominatimGeocodificador g = geocodificador(true);
        servidor.stop(0);

        assertEquals(Optional.empty(), buscar(g));
    }

    @Test
    void respostaQueNaoEJsonEIgnorada() {
        NominatimGeocodificador g = geocodificador(true);

        assertEquals(Optional.empty(), g.interpretar("<html>erro</html>"));
        assertEquals(Optional.empty(), g.interpretar("{}"));
        assertEquals(Optional.empty(), g.interpretar("[{\"lat\":\"abc\"}]"));
    }

    @Test
    void respeitaOIntervaloMinimoEntreConsultas() {
        respostas.add("[]");
        respostas.add("[]");

        long inicio = System.currentTimeMillis();
        buscar(geocodificador(true));
        long duracao = System.currentTimeMillis() - inicio;

        // Duas consultas (rua e bairro): a segunda espera pelo menos ~1 s depois da primeira.
        assertTrue(duracao >= 1000, "esperava intervalo entre as consultas, mas levou " + duracao + " ms");
    }
}
