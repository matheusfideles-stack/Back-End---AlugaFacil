package com.larenegocios.alugafacil.domain.pagamento;

public enum MetodoPagamento {
    PIX,
    BOLETO,
    CARTAO_CREDITO,
    CARTAO_DEBITO;

    public boolean ehCartao() {
        return this == CARTAO_CREDITO || this == CARTAO_DEBITO;
    }
}
