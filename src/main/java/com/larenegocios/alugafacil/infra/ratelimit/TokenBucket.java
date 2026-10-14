package com.larenegocios.alugafacil.infra.ratelimit;

import java.util.function.LongSupplier;

/** Balde de fichas: reabastece {@code fichasPorSegundo} por segundo, ate o limite de {@code capacidade}. */
class TokenBucket {

    private static final double NANOS_POR_SEGUNDO = 1_000_000_000d;

    private final double capacidade;
    private final double fichasPorSegundo;
    private final LongSupplier relogioNanos;

    private double fichas;
    private long ultimaRecarga;
    private long ultimoUso;

    TokenBucket(double fichasPorSegundo, int capacidade, LongSupplier relogioNanos) {
        this.fichasPorSegundo = fichasPorSegundo;
        this.capacidade = capacidade;
        this.relogioNanos = relogioNanos;
        this.fichas = capacidade;
        this.ultimaRecarga = relogioNanos.getAsLong();
        this.ultimoUso = this.ultimaRecarga;
    }

    synchronized boolean tentarConsumir() {
        recarregar();
        if (fichas >= 1) {
            fichas -= 1;
            ultimoUso = ultimaRecarga;
            return true;
        }
        return false;
    }

    /** Segundos ate haver uma ficha disponivel, arredondado para cima (minimo 1). */
    synchronized long segundosAteProximaFicha() {
        recarregar();
        if (fichas >= 1) {
            return 1;
        }
        return Math.max(1, (long) Math.ceil((1 - fichas) / fichasPorSegundo));
    }

    synchronized boolean ocioso(long limiteNanos) {
        return relogioNanos.getAsLong() - ultimoUso > limiteNanos;
    }

    private void recarregar() {
        long agora = relogioNanos.getAsLong();
        double decorridoSegundos = (agora - ultimaRecarga) / NANOS_POR_SEGUNDO;
        fichas = Math.min(capacidade, fichas + decorridoSegundos * fichasPorSegundo);
        ultimaRecarga = agora;
    }
}
