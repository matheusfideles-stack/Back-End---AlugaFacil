package com.larenegocios.alugafacil.domain.pagamento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculoDeAtrasoTest {

    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 12, 10);
    private static final BigDecimal ALUGUEL = new BigDecimal("1800.00");

    private CalculoDeAtraso.Resultado em(int diasDepoisDoVencimento) {
        return CalculoDeAtraso.calcular(ALUGUEL, VENCIMENTO, VENCIMENTO.plusDays(diasDepoisDoVencimento));
    }

    @Test
    void antesDoVencimentoNaoHaAcrescimo() {
        var r = em(-5);

        assertEquals(new BigDecimal("1800.00"), r.total());
        assertEquals(new BigDecimal("0.00"), r.multa());
        assertEquals(new BigDecimal("0.00"), r.juros());
        assertFalse(r.emAtraso());
    }

    @Test
    void noProprioDiaDoVencimentoNaoHaAcrescimo() {
        var r = em(0);

        assertEquals(new BigDecimal("1800.00"), r.total());
        assertEquals(0, r.diasEmAtraso());
    }

    @Test
    void umDiaDeAtrasoCobraMultaDeDoisPorCentoEUmTrintaAvosDosJuros() {
        var r = em(1);

        assertEquals(new BigDecimal("36.00"), r.multa());
        assertEquals(new BigDecimal("0.60"), r.juros());
        assertEquals(new BigDecimal("1836.60"), r.total());
        assertTrue(r.emAtraso());
    }

    @Test
    void dezDiasDeAtraso() {
        var r = em(10);

        assertEquals(new BigDecimal("36.00"), r.multa());
        assertEquals(new BigDecimal("6.00"), r.juros());
        assertEquals(new BigDecimal("1842.00"), r.total());
        assertEquals(10, r.diasEmAtraso());
    }

    @Test
    void umMesDeAtrasoCobraUmPorCentoDeJuros() {
        var r = em(30);

        assertEquals(new BigDecimal("18.00"), r.juros());
        assertEquals(new BigDecimal("1854.00"), r.total());
    }

    @Test
    void multaCobraUmaVezSoOsJurosCrescemComOsDias() {
        assertEquals(em(5).multa(), em(60).multa());
        assertTrue(em(60).juros().compareTo(em(5).juros()) > 0);
    }

    @Test
    void arredondaParaCentavos() {
        var r = CalculoDeAtraso.calcular(new BigDecimal("1000.50"), VENCIMENTO, VENCIMENTO.plusDays(7));

        assertEquals(new BigDecimal("20.01"), r.multa());
        assertEquals(new BigDecimal("2.33"), r.juros());
        assertEquals(new BigDecimal("1022.84"), r.total());
    }

    @Test
    void valorBaseNuncaMuda() {
        assertEquals(new BigDecimal("1800.00"), em(45).valorBase());
    }
}
