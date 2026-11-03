package com.larenegocios.alugafacil.domain.pagamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Aluguel pago depois do vencimento: multa unica de 2% mais juros de mora de 1% ao mes, contados por dia (1/30 do mes).
// Sao os mesmos valores citados no contrato gerado pelo sistema. Pagar no proprio dia do vencimento nao tem acrescimo.
public final class CalculoDeAtraso {

    public static final BigDecimal MULTA = new BigDecimal("0.02");
    public static final BigDecimal JUROS_AO_MES = new BigDecimal("0.01");
    private static final BigDecimal DIAS_DO_MES = BigDecimal.valueOf(30);

    public record Resultado(BigDecimal valorBase, BigDecimal multa, BigDecimal juros, BigDecimal total, long diasEmAtraso) {

        public boolean emAtraso() {
            return diasEmAtraso > 0;
        }
    }

    private CalculoDeAtraso() {
    }

    public static Resultado calcular(BigDecimal valorBase, LocalDate vencimento, LocalDate hoje) {
        BigDecimal base = valorBase.setScale(2, RoundingMode.HALF_UP);
        long dias = ChronoUnit.DAYS.between(vencimento, hoje);
        if (dias <= 0) {
            return new Resultado(base, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), base, 0);
        }
        BigDecimal multa = base.multiply(MULTA).setScale(2, RoundingMode.HALF_UP);
        BigDecimal juros = base.multiply(JUROS_AO_MES).multiply(BigDecimal.valueOf(dias))
                .divide(DIAS_DO_MES, 2, RoundingMode.HALF_UP);
        return new Resultado(base, multa, juros, base.add(multa).add(juros), dias);
    }
}
