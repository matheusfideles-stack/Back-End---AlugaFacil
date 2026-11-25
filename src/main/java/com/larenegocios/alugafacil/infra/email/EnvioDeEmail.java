package com.larenegocios.alugafacil.infra.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class EnvioDeEmail {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeEmail.class);
    private static final URI API_BREVO = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final URI API_RESEND = URI.create("https://api.resend.com/emails");
    private static final URI API_SMTP2GO = URI.create("https://api.smtp2go.com/v3/email/send");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    // Uma variavel de ambiente vazia (SPRING_MAIL_HOST=) ainda cria o JavaMailSender, mas sem servidor: nao conta como configurado.
    @Value("${spring.mail.host:}")
    private String servidor = "";

    // Hospedagens como o Railway bloqueiam as portas SMTP. Com esta chave, o envio vai pela API da Brevo (porta 443).
    @Value("${BREVO_API_KEY:}")
    private String chaveBrevo = "";

    // Resend: envia pela API HTTPS (porta 443). Sem dominio verificado, so entrega para o e-mail da propria conta do Resend.
    @Value("${RESEND_API_KEY:}")
    private String chaveResend = "";

    // SMTP2GO: envio pela API HTTPS (porta 443). Aceita um remetente verificado, sem dominio proprio.
    @Value("${SMTP2GO_API_KEY:}")
    private String chaveSmtp2go = "";

    public EnvioDeEmail(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.email-remetente}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void avisarSituacaoDoEmail() {
        if (enviaDeVerdade()) {
            log.info("E-mail configurado: mensagens (codigo de verificacao, recuperacao de senha e contato) serao enviadas de verdade.");
        } else {
            log.warn("E-mail NAO configurado: nenhuma mensagem sera enviada, so aparecem no log. "
                    + "Veja docs/EMAIL.md para ativar o envio.");
        }
    }

    public boolean enviaDeVerdade() {
        if (chaveSmtp2go != null && !chaveSmtp2go.isBlank()) {
            return true;
        }
        if (chaveResend != null && !chaveResend.isBlank()) {
            return true;
        }
        if (chaveBrevo != null && !chaveBrevo.isBlank()) {
            return true;
        }
        return mailSender.getIfAvailable() != null && servidor != null && !servidor.isBlank();
    }

    public void enviar(String para, String assunto, String texto) {
        if (!enviaDeVerdade()) {
            log.warn("SMTP nao configurado: e-mail NAO enviado.\nPara: {}\nAssunto: {}\n{}", para, assunto, texto);
            return;
        }

        if (chaveSmtp2go != null && !chaveSmtp2go.isBlank()) {
            enviarPelaApiDoSmtp2go(para, assunto, texto);
            return;
        }
        if (chaveResend != null && !chaveResend.isBlank()) {
            enviarPelaApiDoResend(para, assunto, texto);
            return;
        }
        if (chaveBrevo != null && !chaveBrevo.isBlank()) {
            enviarPelaApiDaBrevo(para, assunto, texto);
            return;
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        mailSender.getObject().send(mensagem);
    }

    private void enviarPelaApiDoSmtp2go(String para, String assunto, String texto) {
        Map<String, Object> corpo = Map.of(
                "sender", remetente,
                "to", List.of(para),
                "subject", assunto,
                "text_body", texto);
        try {
            HttpRequest pedido = HttpRequest.newBuilder(API_SMTP2GO)
                    .timeout(Duration.ofSeconds(15))
                    .header("X-Smtp2go-Api-Key", chaveSmtp2go)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() / 100 != 2) {
                throw new IllegalStateException("API da SMTP2GO recusou o e-mail: HTTP " + resposta.statusCode() + " " + resposta.body());
            }
            log.info("E-mail aceito pela API da SMTP2GO (HTTP {}, assunto: {}): {}", resposta.statusCode(), assunto, resposta.body());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao chamar a API da SMTP2GO", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail interrompido", e);
        }
    }

    private void enviarPelaApiDoResend(String para, String assunto, String texto) {
        Map<String, Object> corpo = Map.of(
                "from", remetente,
                "to", List.of(para),
                "subject", assunto,
                "text", texto);
        try {
            HttpRequest pedido = HttpRequest.newBuilder(API_RESEND)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + chaveResend)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() / 100 != 2) {
                throw new IllegalStateException("API do Resend recusou o e-mail: HTTP " + resposta.statusCode() + " " + resposta.body());
            }
            log.info("E-mail aceito pela API do Resend (HTTP {}, assunto: {}): {}", resposta.statusCode(), assunto, resposta.body());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao chamar a API do Resend", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail interrompido", e);
        }
    }

    private void enviarPelaApiDaBrevo(String para, String assunto, String texto) {
        Map<String, Object> corpo = Map.of(
                "sender", Map.of("email", remetente),
                "to", List.of(Map.of("email", para)),
                "subject", assunto,
                "textContent", texto);
        try {
            HttpRequest pedido = HttpRequest.newBuilder(API_BREVO)
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", chaveBrevo)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() / 100 != 2) {
                throw new IllegalStateException("API da Brevo recusou o e-mail: HTTP " + resposta.statusCode());
            }
            log.info("E-mail aceito pela API da Brevo (HTTP {}, assunto: {}): {}", resposta.statusCode(), assunto, resposta.body());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao chamar a API da Brevo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail interrompido", e);
        }
    }
}
