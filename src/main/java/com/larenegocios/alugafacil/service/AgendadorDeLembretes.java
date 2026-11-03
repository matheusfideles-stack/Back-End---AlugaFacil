package com.larenegocios.alugafacil.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.ZoneId;

// Uma vez por dia, no horario de Brasilia. Desligue com APP_LEMBRETES_ATIVO=false (os testes ja desligam).
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.lembretes.ativo", havingValue = "true", matchIfMissing = true)
public class AgendadorDeLembretes {

    private static final Logger log = LoggerFactory.getLogger(AgendadorDeLembretes.class);

    @Autowired
    private LembreteDePagamentoService lembretes;

    @Scheduled(cron = "${app.lembretes.cron:0 0 9 * * *}", zone = "America/Sao_Paulo")
    public void rodar() {
        try {
            lembretes.enviarLembretes(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
        } catch (RuntimeException e) {
            log.error("A rotina diaria de lembretes de pagamento falhou", e);
        }
    }
}
