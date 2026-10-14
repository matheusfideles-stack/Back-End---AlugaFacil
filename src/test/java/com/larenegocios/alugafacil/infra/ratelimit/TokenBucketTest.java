package com.larenegocios.alugafacil.infra.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    private final AtomicLong agora = new AtomicLong(0);

    private void avancar(long tempo, TimeUnit unidade) {
        agora.addAndGet(unidade.toNanos(tempo));
    }

    @Test
    void permiteRajadaAteACapacidadeEBloqueiaDepois() {
        TokenBucket balde = new TokenBucket(1, 3, agora::get);

        assertTrue(balde.tentarConsumir());
        assertTrue(balde.tentarConsumir());
        assertTrue(balde.tentarConsumir());
        assertFalse(balde.tentarConsumir());
    }

    @Test
    void reabasteceNaVazaoConfigurada() {
        TokenBucket balde = new TokenBucket(2, 2, agora::get);
        balde.tentarConsumir();
        balde.tentarConsumir();
        assertFalse(balde.tentarConsumir());

        avancar(500, TimeUnit.MILLISECONDS);

        assertTrue(balde.tentarConsumir(), "meio segundo a 2/s devolve uma ficha");
        assertFalse(balde.tentarConsumir());
    }

    @Test
    void nuncaAcumulaAlemDaCapacidade() {
        TokenBucket balde = new TokenBucket(10, 2, agora::get);

        avancar(1, TimeUnit.HOURS);

        assertTrue(balde.tentarConsumir());
        assertTrue(balde.tentarConsumir());
        assertFalse(balde.tentarConsumir());
    }

    @Test
    void informaQuantoTempoFaltaParaLiberar() {
        TokenBucket balde = new TokenBucket(0.5, 1, agora::get);
        balde.tentarConsumir();

        assertEquals(2, balde.segundosAteProximaFicha());

        avancar(1, TimeUnit.SECONDS);
        assertEquals(1, balde.segundosAteProximaFicha());
    }

    @Test
    void detectaBaldeOcioso() {
        TokenBucket balde = new TokenBucket(1, 1, agora::get);
        balde.tentarConsumir();

        avancar(10, TimeUnit.MINUTES);

        assertTrue(balde.ocioso(TimeUnit.MINUTES.toNanos(5)));
        assertFalse(balde.ocioso(TimeUnit.MINUTES.toNanos(20)));
    }
}
