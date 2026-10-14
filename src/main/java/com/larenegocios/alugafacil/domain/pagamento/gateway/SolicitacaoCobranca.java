package com.larenegocios.alugafacil.domain.pagamento.gateway;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SolicitacaoCobranca(
        Long pagamentoId,
        BigDecimal valor,
        MetodoPagamento metodo,
        String descricao,
        Pagador pagador,
        LocalDate vencimento,
        String tokenCartao,
        Integer parcelas,
        String bandeiraCartao
) {
}
