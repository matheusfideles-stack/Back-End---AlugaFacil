package com.larenegocios.alugafacil.infra.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

@Component
public class RateLimiter {

    private static final int CHAMADAS_ENTRE_LIMPEZAS = 1000;
    private static final long TEMPO_OCIOSO_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final RateLimitProperties propriedades;
    private final LongSupplier relogioNanos;
    private final ConcurrentHashMap<String, TokenBucket> baldes = new ConcurrentHashMap<>();
    private final AtomicInteger chamadas = new AtomicInteger();

    @Autowired
    public RateLimiter(RateLimitProperties propriedades) {
        this(propriedades, System::nanoTime);
    }

    RateLimiter(RateLimitProperties propriedades, LongSupplier relogioNanos) {
        this.propriedades = propriedades;
        this.relogioNanos = relogioNanos;
    }

    public record Decisao(boolean permitida, long segundosParaTentarNovamente) {
    }

    public Decisao tentar(CategoriaRateLimit categoria, String cliente) {
        limparOciososDeVezEmQuando();

        RateLimitProperties.Regra regra = propriedades.regraDe(categoria);
        TokenBucket balde = baldes.computeIfAbsent(categoria + "|" + cliente,
                chave -> new TokenBucket(regra.getRequestsPerSecond(), regra.getBurst(), relogioNanos));

        if (balde.tentarConsumir()) {
            return new Decisao(true, 0);
        }
        return new Decisao(false, balde.segundosAteProximaFicha());
    }

    private void limparOciososDeVezEmQuando() {
        if (chamadas.incrementAndGet() % CHAMADAS_ENTRE_LIMPEZAS == 0) {
            baldes.values().removeIf(balde -> balde.ocioso(TEMPO_OCIOSO_NANOS));
        }
    }
}
