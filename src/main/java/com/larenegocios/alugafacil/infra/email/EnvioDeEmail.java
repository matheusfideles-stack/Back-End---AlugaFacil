package com.larenegocios.alugafacil.infra.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EnvioDeEmail {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeEmail.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;

    public EnvioDeEmail(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.email-remetente}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void avisarSituacaoDoEmail() {
        if (enviaDeVerdade()) {
            log.info("E-mail configurado: mensagens (recuperacao de senha e contato) serao enviadas de verdade.");
        } else {
            log.warn("E-mail NAO configurado: nenhuma mensagem sera enviada, so aparecem no log. "
                    + "Veja config/application.yml.exemplo para ativar o envio.");
        }
    }

    public boolean enviaDeVerdade() {
        return mailSender.getIfAvailable() != null;
    }

    public void enviar(String para, String assunto, String texto) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP nao configurado: e-mail NAO enviado.\nPara: {}\nAssunto: {}\n{}", para, assunto, texto);
            return;
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        sender.send(mensagem);
    }
}
