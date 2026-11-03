package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.pagamento.EstagioDeLembrete;
import com.larenegocios.alugafacil.domain.pagamento.LembretePagamento;
import com.larenegocios.alugafacil.domain.pagamento.LembretePagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

// Avisa por e-mail o locatario que esta perto de vencer ou atrasado. Nao cria cobranca nem mexe em dinheiro:
// so lembra a pessoa de abrir a tela de pagamentos.
@Service
public class LembreteDePagamentoService {

    private static final Logger log = LoggerFactory.getLogger(LembreteDePagamentoService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<StatusPagamento> JA_RESOLVIDOS = Set.of(StatusPagamento.PAGO, StatusPagamento.PROCESSANDO);

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private LembretePagamentoRepository lembreteRepository;

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Value("${app.url-base}")
    private String urlBase;

    // Devolve quantos lembretes (estagios) foram enviados nesta rodada.
    @Transactional
    public int enviarLembretes(LocalDate hoje) {
        int enviados = 0;
        for (Contrato contrato : contratoRepository.findByStatus(StatusContrato.ATIVO)) {
            // O mes anterior cobre o atraso que atravessa a virada; o proximo, o aviso antecipado de um vencimento no dia 1 a 3.
            for (YearMonth mes : new YearMonth[]{YearMonth.from(hoje).minusMonths(1), YearMonth.from(hoje),
                    YearMonth.from(hoje).plusMonths(1)}) {
                if (lembrar(contrato, mes, hoje)) {
                    enviados++;
                }
            }
        }
        if (enviados > 0) {
            log.info("Lembretes de pagamento enviados: {}", enviados);
        }
        return enviados;
    }

    private boolean lembrar(Contrato contrato, YearMonth mes, LocalDate hoje) {
        LocalDate vencimento = contrato.vencimentoEm(mes);
        // So conta o vencimento que cai durante o contrato e depois de ele existir no sistema.
        if (vencimento.isBefore(contrato.getDataInicio()) || vencimento.isAfter(contrato.getDataFim())
                || vencimento.isBefore(contrato.getCriadoEm().toLocalDate())) {
            return false;
        }
        String referencia = mes.toString();
        boolean quitado = pagamentoRepository.findByContratoIdAndReferenciaMes(contrato.getId(), referencia)
                .map(p -> JA_RESOLVIDOS.contains(p.getStatus()))
                .orElse(false);
        if (quitado) {
            return false;
        }
        EstagioDeLembrete estagio = EstagioDeLembrete.maisRecente(hoje, vencimento).orElse(null);
        if (estagio == null
                || lembreteRepository.existsByContratoIdAndReferenciaMesAndEstagio(contrato.getId(), referencia, estagio)) {
            return false;
        }

        try {
            avisarLocatario(contrato, mes, vencimento, estagio, hoje);
        } catch (RuntimeException e) {
            // Sem registro, a proxima rodada tenta de novo.
            log.error("Nao foi possivel mandar o lembrete {} do contrato {} ({})", estagio, contrato.getId(), referencia, e);
            return false;
        }
        if (estagio == EstagioDeLembrete.ATRASO_3) {
            try {
                avisarLocador(contrato, mes, vencimento, hoje);
            } catch (RuntimeException e) {
                log.error("Nao foi possivel avisar o locador do contrato {} sobre o atraso de {}", contrato.getId(), referencia, e);
            }
        }

        LembretePagamento registro = new LembretePagamento();
        registro.setContrato(contrato);
        registro.setReferenciaMes(referencia);
        registro.setEstagio(estagio);
        lembreteRepository.save(registro);
        return true;
    }

    private void avisarLocatario(Contrato contrato, YearMonth mes, LocalDate vencimento, EstagioDeLembrete estagio,
                                 LocalDate hoje) {
        String titulo = contrato.getImovel().getTitulo();
        String quando = switch (estagio) {
            case ANTECEDENCIA -> "vence em " + ChronoUnit.DAYS.between(hoje, vencimento)
                    + " dia(s), em " + DATA.format(vencimento);
            case VENCE_HOJE -> "vence hoje (" + DATA.format(vencimento) + ")";
            default -> "esta atrasado ha " + ChronoUnit.DAYS.between(vencimento, hoje)
                    + " dia(s) (venceu em " + DATA.format(vencimento) + ")";
        };
        String assunto = "AlugaFacil - o aluguel de " + titulo + " (" + mesExibido(mes) + ") "
                + (estagio.ehAtraso() ? "esta atrasado" : estagio == EstagioDeLembrete.VENCE_HOJE ? "vence hoje" : "esta para vencer");
        String texto = "Ola, " + contrato.getLocatario().getNome() + "!\n\n"
                + "O aluguel de " + mesExibido(mes) + " do imovel " + titulo + " " + quando + ".\n"
                + "Valor: " + moeda(contrato) + "\n\n"
                + "Pague por Pix, boleto ou cartao em " + urlBase + "/pagamentos.html.\n\n"
                + "Se voce ja pagou, pode ignorar este aviso: a confirmacao pode levar algum tempo.";
        envioDeEmail.enviar(contrato.getLocatario().getEmail(), assunto, texto);
    }

    private void avisarLocador(Contrato contrato, YearMonth mes, LocalDate vencimento, LocalDate hoje) {
        String titulo = contrato.getImovel().getTitulo();
        long dias = ChronoUnit.DAYS.between(vencimento, hoje);
        String assunto = "AlugaFacil - aluguel atrasado: " + titulo + " (" + mesExibido(mes) + ")";
        String texto = "Ola, " + contrato.getImovel().getLocador().getNome() + "!\n\n"
                + contrato.getLocatario().getNome() + " ainda nao pagou o aluguel de " + mesExibido(mes)
                + " do imovel " + titulo + ". Venceu em " + DATA.format(vencimento) + " (" + dias + " dia(s) de atraso).\n"
                + "Valor: " + moeda(contrato) + "\n\n"
                + "O locatario ja foi avisado por e-mail. Acompanhe os recebimentos em " + urlBase + "/financeiro.html.";
        envioDeEmail.enviar(contrato.getImovel().getLocador().getEmail(), assunto, texto);
    }

    // "2026-12" vira "12/2026".
    private static String mesExibido(YearMonth mes) {
        return String.format("%02d/%d", mes.getMonthValue(), mes.getYear());
    }

    private static String moeda(Contrato contrato) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(contrato.getValorMensal())
                .replace(' ', ' ');
    }
}
