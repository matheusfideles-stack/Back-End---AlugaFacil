package com.larenegocios.alugafacil.domain.pagamento.gateway;

public interface PaymentGateway {

    RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao);

    /**
     * Reconsulta a cobranca no provedor. Usado pelo webhook e pela sincronizacao manual,
     * ja que Pix e boleto sao confirmados de forma assincrona.
     */
    RespostaCobranca consultarCobranca(String idTransacaoGateway);

    void estornarCobranca(String idTransacaoGateway);
}
