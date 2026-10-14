package com.larenegocios.alugafacil.dto;

/**
 * Configuracao publica do meio de pagamento, consumida pelo frontend.
 *
 * @param provider  gateway ativo ("fake" ou "mercadopago")
 * @param publicKey chave publica usada para tokenizar o cartao no navegador
 */
public record DadosConfiguracaoPagamento(String provider, String publicKey) {
}
