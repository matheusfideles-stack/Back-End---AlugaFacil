package com.larenegocios.alugafacil.infra.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ratelimit")
@Getter
@Setter
public class RateLimitProperties {

    private boolean enabled = true;

    private Regra autenticacao = new Regra(2, 5);
    private Regra pagamentos = new Regra(2, 5);
    private Regra webhooks = new Regra(20, 50);
    private Regra geral = new Regra(10, 20);

    public Regra regraDe(CategoriaRateLimit categoria) {
        return switch (categoria) {
            case AUTENTICACAO -> autenticacao;
            case PAGAMENTOS -> pagamentos;
            case WEBHOOKS -> webhooks;
            case GERAL -> geral;
        };
    }

    @Getter
    @Setter
    public static class Regra {

        private double requestsPerSecond;
        private int burst;

        public Regra() {
        }

        public Regra(double requestsPerSecond, int burst) {
            this.requestsPerSecond = requestsPerSecond;
            this.burst = burst;
        }
    }
}
