package com.larenegocios.alugafacil.domain.pagamento.gateway;

public interface PaymentGateway {

    RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao);

    RespostaCobranca consultarCobranca(String idTransacaoGateway);

    void estornarCobranca(String idTransacaoGateway);
}
