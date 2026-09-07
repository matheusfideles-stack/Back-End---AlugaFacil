package com.larenegocios.alugafacil.domain.pagamento.gateway;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;

import java.math.BigDecimal;

/**
 * Dados enviados ao gateway de pagamento para gerar uma cobranca.
 */
public record SolicitacaoCobranca(
        Long pagamentoId,
        BigDecimal valor,
        MetodoPagamento metodo,
        String descricao,
        String emailPagador,
        String cpfPagador
) {
}
