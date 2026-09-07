package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.pagamento.Pagamento;

/**
 * resultado da geracao de uma cobranca
 */
public record CobrancaGerada(Pagamento pagamento, String linkPagamento) {
}
