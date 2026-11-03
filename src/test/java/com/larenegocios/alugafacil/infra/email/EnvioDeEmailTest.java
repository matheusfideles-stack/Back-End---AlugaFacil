package com.larenegocios.alugafacil.infra.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvioDeEmailTest {

    static class EnviadorQueAnota extends JavaMailSenderImpl {
        final List<SimpleMailMessage> enviadas = new ArrayList<>();

        @Override
        public void send(SimpleMailMessage mensagem) {
            enviadas.add(mensagem);
        }
    }

    private EnvioDeEmail envioCom(JavaMailSender enviador, String servidor) {
        DefaultListableBeanFactory fabrica = new DefaultListableBeanFactory();
        if (enviador != null) {
            fabrica.registerSingleton("enviador", enviador);
        }
        EnvioDeEmail envio = new EnvioDeEmail(fabrica.getBeanProvider(JavaMailSender.class), "nao-responda@exemplo.com");
        ReflectionTestUtils.setField(envio, "servidor", servidor);
        return envio;
    }

    @Test
    void comEnviadorEServidorEnviaDeVerdade() {
        EnviadorQueAnota enviador = new EnviadorQueAnota();
        EnvioDeEmail envio = envioCom(enviador, "smtp.exemplo.com");

        envio.enviar("ana@teste.com", "Assunto", "Texto");

        assertTrue(envio.enviaDeVerdade());
        assertEquals(1, enviador.enviadas.size());
        assertEquals("ana@teste.com", enviador.enviadas.get(0).getTo()[0]);
        assertEquals("nao-responda@exemplo.com", enviador.enviadas.get(0).getFrom());
        assertEquals("Assunto", enviador.enviadas.get(0).getSubject());
    }

    @Test
    void servidorEmBrancoNaoContaComoConfiguradoENaoEnvia() {
        EnviadorQueAnota enviador = new EnviadorQueAnota();
        EnvioDeEmail envio = envioCom(enviador, "");

        envio.enviar("ana@teste.com", "Assunto", "Texto");

        assertFalse(envio.enviaDeVerdade());
        assertTrue(enviador.enviadas.isEmpty());
    }

    @Test
    void semEnviadorNaoEnviaENaoLancaErro() {
        EnvioDeEmail envio = envioCom(null, "smtp.exemplo.com");

        envio.enviar("ana@teste.com", "Assunto", "Texto");

        assertFalse(envio.enviaDeVerdade());
    }
}
