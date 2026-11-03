package com.larenegocios.alugafacil.domain.pagamento;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstagioDeLembreteTest {

    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 3, 10);

    private static Optional<EstagioDeLembrete> em(int diasDepoisDoVencimento) {
        return EstagioDeLembrete.maisRecente(VENCIMENTO.plusDays(diasDepoisDoVencimento), VENCIMENTO);
    }

    @Test
    void antesDosTresDiasNaoHaAviso() {
        assertEquals(Optional.empty(), em(-4));
        assertEquals(Optional.empty(), em(-30));
    }

    @Test
    void cadaMarcoCaiNoDiaCerto() {
        assertEquals(Optional.of(EstagioDeLembrete.ANTECEDENCIA), em(-3));
        assertEquals(Optional.of(EstagioDeLembrete.VENCE_HOJE), em(0));
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_1), em(1));
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_3), em(3));
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_7), em(7));
    }

    @Test
    void diasEntreMarcosContinuamNoMarcoAnterior() {
        assertEquals(Optional.of(EstagioDeLembrete.ANTECEDENCIA), em(-2));
        assertEquals(Optional.of(EstagioDeLembrete.ANTECEDENCIA), em(-1));
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_1), em(2));
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_3), em(6));
    }

    @Test
    void depoisDoUltimoMarcoOEstagioNaoMuda() {
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_7), em(8));
        assertEquals(Optional.of(EstagioDeLembrete.ATRASO_7), em(90));
    }

    @Test
    void soOsEstagiosDeAtrasoSaoAtraso() {
        assertFalse(EstagioDeLembrete.ANTECEDENCIA.ehAtraso());
        assertFalse(EstagioDeLembrete.VENCE_HOJE.ehAtraso());
        assertTrue(EstagioDeLembrete.ATRASO_1.ehAtraso());
        assertTrue(EstagioDeLembrete.ATRASO_7.ehAtraso());
    }

    @Test
    void dataDoEstagioEhRelativaAoVencimento() {
        assertEquals(LocalDate.of(2026, 3, 7), EstagioDeLembrete.ANTECEDENCIA.dataPara(VENCIMENTO));
        assertEquals(LocalDate.of(2026, 3, 17), EstagioDeLembrete.ATRASO_7.dataPara(VENCIMENTO));
    }
}
