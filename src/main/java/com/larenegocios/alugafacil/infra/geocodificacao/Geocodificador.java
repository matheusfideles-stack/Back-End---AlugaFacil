package com.larenegocios.alugafacil.infra.geocodificacao;

import java.util.Optional;

// Transforma um endereco em latitude e longitude. Vazio quando nao acha: o imovel continua valendo, so nao ganha pino exato no mapa.
public interface Geocodificador {

    Optional<Coordenadas> localizar(String logradouro, String numero, String bairro, String cidade, String uf);
}
