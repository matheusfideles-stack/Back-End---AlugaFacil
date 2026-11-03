package com.larenegocios.alugafacil.infra.geocodificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

// Usa o Nominatim, o buscador de enderecos do OpenStreetMap. A politica de uso pede no maximo 1 consulta por segundo e
// um User-Agent que identifique a aplicacao: por isso o intervalo minimo entre chamadas e a identificacao abaixo.
@Component
public class NominatimGeocodificador implements Geocodificador {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodificador.class);
    private static final long INTERVALO_MINIMO_MS = 1100;

    private final boolean ativa;
    private final ObjectMapper objectMapper;
    private final RestClient cliente;
    private long ultimaConsulta = 0;

    public NominatimGeocodificador(ObjectMapper objectMapper,
                                   @Value("${app.geocodificacao.ativa:true}") boolean ativa,
                                   @Value("${app.geocodificacao.url:https://nominatim.openstreetmap.org}") String url,
                                   @Value("${app.geocodificacao.user-agent:AlugaFacil/1.0 (projeto academico)}") String userAgent) {
        this.objectMapper = objectMapper;
        this.ativa = ativa;

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.cliente = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    // Primeiro tenta a rua com numero; se o OpenStreetMap nao conhece, cai para o bairro (pino aproximado).
    @Override
    public Optional<Coordenadas> localizar(String logradouro, String numero, String bairro, String cidade, String uf) {
        if (!ativa) {
            return Optional.empty();
        }
        Optional<Coordenadas> exata = consultar(String.join(", ", logradouro + " " + numero, bairro, cidade, uf, "Brasil"));
        if (exata.isPresent()) {
            return exata;
        }
        return consultar(String.join(", ", bairro, cidade, uf, "Brasil"));
    }

    private synchronized Optional<Coordenadas> consultar(String consulta) {
        esperarIntervalo();
        try {
            String corpo = cliente.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", "br")
                            .queryParam("q", consulta)
                            .build())
                    .retrieve()
                    .body(String.class);
            return interpretar(corpo);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel consultar o endereco '{}': {}", consulta, e.getMessage());
            return Optional.empty();
        }
    }

    Optional<Coordenadas> interpretar(String corpo) {
        try {
            JsonNode resultados = objectMapper.readTree(corpo);
            if (resultados == null || !resultados.isArray() || resultados.isEmpty()) {
                return Optional.empty();
            }
            JsonNode primeiro = resultados.get(0);
            double latitude = primeiro.path("lat").asDouble(Double.NaN);
            double longitude = primeiro.path("lon").asDouble(Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                return Optional.empty();
            }
            return Optional.of(new Coordenadas(latitude, longitude));
        } catch (Exception e) {
            log.warn("Resposta do geocodificador nao entendida: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void esperarIntervalo() {
        long espera = INTERVALO_MINIMO_MS - (System.currentTimeMillis() - ultimaConsulta);
        if (espera > 0) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ultimaConsulta = System.currentTimeMillis();
    }
}
