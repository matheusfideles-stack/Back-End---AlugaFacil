package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.pagamento.CalculoDeAtraso;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DadosValorAtualizado(
        String referenciaMes,
        LocalDate vencimento,
        BigDecimal valorBase,
        BigDecimal multa,
        BigDecimal juros,
        BigDecimal total,
        long diasEmAtraso
) {
    public DadosValorAtualizado(String referenciaMes, LocalDate vencimento, CalculoDeAtraso.Resultado r) {
        this(referenciaMes, vencimento, r.valorBase(), r.multa(), r.juros(), r.total(), r.diasEmAtraso());
    }
}
