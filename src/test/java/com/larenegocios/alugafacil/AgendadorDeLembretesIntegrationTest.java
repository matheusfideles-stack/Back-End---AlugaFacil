package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.service.AgendadorDeLembretes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Com a rotina ligada o contexto precisa subir: um cron invalido derrubaria a aplicacao inteira na subida.
@TestPropertySource(properties = "app.lembretes.ativo=true")
class AgendadorDeLembretesIntegrationTest extends IntegracaoBase {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void rotinaDiariaSobeQuandoLigada() {
        assertNotNull(contexto.getBean(AgendadorDeLembretes.class));
    }
}
