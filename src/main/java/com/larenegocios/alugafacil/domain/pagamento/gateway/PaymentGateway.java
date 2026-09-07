package com.larenegocios.alugafacil.domain.pagamento.gateway;

public interface PaymentGateway {

    RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao);

    void estornarCobranca(String idTransacaoGateway);
}
