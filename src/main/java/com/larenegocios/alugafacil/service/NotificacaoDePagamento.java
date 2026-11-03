package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoConfirmadoEvento;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Roda depois do commit: so avisa de um pagamento que de fato ficou gravado. Falha no e-mail nunca desfaz o pagamento.
@Component
public class NotificacaoDePagamento {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoDePagamento.class);
    private static final DateTimeFormatter DATA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Value("${app.url-base}")
    private String urlBase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoConfirmarPagamento(PagamentoConfirmadoEvento evento) {
        String comprovante = comprovante(evento);

        enviarSemFalhar(evento.emailLocatario(),
                "AlugaFacil - pagamento confirmado (comprovante " + comprovante + ")",
                textoParaOLocatario(evento, comprovante), evento);
        enviarSemFalhar(evento.emailLocador(),
                "AlugaFacil - pagamento recebido: " + evento.tituloImovel() + " (" + mesExibido(evento) + ")",
                textoParaOLocador(evento, comprovante), evento);
    }

    private String textoParaOLocatario(PagamentoConfirmadoEvento e, String comprovante) {
        return "Ola, " + e.nomeLocatario() + "!\n\n"
                + "Recebemos o pagamento do seu aluguel.\n\n"
                + detalhes(e, comprovante) + "\n\n"
                + "O historico dos seus pagamentos esta em " + urlBase + "/pagamentos.html.";
    }

    private String textoParaOLocador(PagamentoConfirmadoEvento e, String comprovante) {
        return "Ola, " + e.nomeLocador() + "!\n\n"
                + e.nomeLocatario() + " pagou o aluguel do seu imovel.\n\n"
                + detalhes(e, comprovante) + "\n\n"
                + "Veja os recebimentos em " + urlBase + "/financeiro.html.";
    }

    private String detalhes(PagamentoConfirmadoEvento e, String comprovante) {
        StringBuilder texto = new StringBuilder()
                .append("Imovel: ").append(e.tituloImovel()).append('\n')
                .append("Referencia: ").append(mesExibido(e)).append('\n')
                .append("Valor: ").append(moeda(e)).append('\n')
                .append("Forma de pagamento: ").append(forma(e.metodo())).append('\n');
        if (e.pagoEm() != null) {
            texto.append("Data: ").append(DATA_E_HORA.format(e.pagoEm())).append('\n');
        }
        return texto.append("Comprovante: ").append(comprovante).toString();
    }

    private static String comprovante(PagamentoConfirmadoEvento e) {
        return String.format("PG-%06d", e.pagamentoId());
    }

    // "2026-12" vira "12/2026".
    private static String mesExibido(PagamentoConfirmadoEvento e) {
        String[] partes = e.referenciaMes().split("-");
        return partes.length == 2 ? partes[1] + "/" + partes[0] : e.referenciaMes();
    }

    private static String moeda(PagamentoConfirmadoEvento e) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(e.valor()).replace(' ', ' ');
    }

    private static String forma(MetodoPagamento metodo) {
        return switch (metodo) {
            case PIX -> "Pix";
            case BOLETO -> "Boleto";
            case CARTAO_DEBITO -> "Cartao de debito";
            case CARTAO_CREDITO -> "Cartao de credito";
        };
    }

    private void enviarSemFalhar(String para, String assunto, String texto, PagamentoConfirmadoEvento evento) {
        try {
            envioDeEmail.enviar(para, assunto, texto);
        } catch (RuntimeException e) {
            log.error("Nao foi possivel avisar {} sobre o pagamento {}", para, evento.pagamentoId(), e);
        }
    }
}
