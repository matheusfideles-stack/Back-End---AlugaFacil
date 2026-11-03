package com.larenegocios.alugafacil.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

// Relogio do sistema, no horario de Brasilia. Os testes fixam uma data (app.relogio.fixo-em) para que multa e juros
// nao dependam do dia em que a suite roda.
@Configuration
public class RelogioConfig {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock relogio(@Value("${app.relogio.fixo-em:}") String fixoEm) {
        if (StringUtils.hasText(fixoEm)) {
            return Clock.fixed(LocalDate.parse(fixoEm.trim()).atTime(12, 0).atZone(BRASILIA).toInstant(), BRASILIA);
        }
        return Clock.system(BRASILIA);
    }
}
