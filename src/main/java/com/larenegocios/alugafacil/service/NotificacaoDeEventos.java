package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.avaliacao.Avaliacao;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.manutencao.Manutencao;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.proposta.Proposta;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.visita.StatusVisita;
import com.larenegocios.alugafacil.domain.visita.Visita;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Avisos por e-mail dos acontecimentos do sistema. Falha ao enviar nunca desfaz a acao que gerou o aviso:
// o erro vai para o log e a pessoa ainda ve tudo na tela. Os textos sao sem acento, como os demais e-mails.
@Component
public class NotificacaoDeEventos {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoDeEventos.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Value("${app.url-base}")
    private String urlBase;

    // ----- proposta de aluguel

    public void propostaRecebida(Proposta p) {
        Usuario locador = p.getImovel().getLocador();
        enviar(locador.getEmail(), "AlugaFacil - nova proposta para " + p.getImovel().getTitulo(),
                "Ola, " + locador.getNome() + "!\n\n"
                        + p.getLocatario().getNome() + " quer alugar o imovel " + p.getImovel().getTitulo() + ".\n"
                        + "Periodo: " + DATA.format(p.getDataInicio()) + " a " + DATA.format(p.getDataFim()) + "\n"
                        + "Valor mensal: " + moeda(valorMensal(p.getImovel())) + ", vencimento no dia " + p.getDiaVencimento() + "\n"
                        + (p.getMensagem() == null ? "" : "Mensagem: " + p.getMensagem() + "\n")
                        + "\nAceite ou recuse em " + urlBase + "/inquilinos.html.");
    }

    public void propostaAceita(Proposta p) {
        enviar(p.getLocatario().getEmail(), "AlugaFacil - sua proposta foi aceita: " + p.getImovel().getTitulo(),
                "Ola, " + p.getLocatario().getNome() + "!\n\n"
                        + "O proprietario aceitou sua proposta para " + p.getImovel().getTitulo() + ". O contrato ja esta ativo.\n"
                        + "Valor mensal: " + moeda(valorMensal(p.getImovel())) + ", vencimento no dia " + p.getDiaVencimento() + "\n\n"
                        + "Pague o primeiro aluguel em " + urlBase + "/pagamentos.html.");
    }

    public void propostaRecusada(Proposta p) {
        enviar(p.getLocatario().getEmail(), "AlugaFacil - sua proposta nao foi aceita: " + p.getImovel().getTitulo(),
                "Ola, " + p.getLocatario().getNome() + "!\n\n"
                        + "O proprietario nao aceitou sua proposta para " + p.getImovel().getTitulo() + ".\n"
                        + (p.getMotivoRecusa() == null ? "" : "Motivo: " + p.getMotivoRecusa() + "\n")
                        + "\nBusque outros imoveis em " + urlBase + "/imoveis.html.");
    }

    // ----- visita

    public void visitaSolicitada(Visita v) {
        Usuario locador = v.getImovel().getLocador();
        enviar(locador.getEmail(), "AlugaFacil - pedido de visita: " + v.getImovel().getTitulo(),
                "Ola, " + locador.getNome() + "!\n\n"
                        + v.getLocatario().getNome() + " quer visitar " + v.getImovel().getTitulo() + " em "
                        + DATA_E_HORA.format(v.getDataHora()) + ".\n"
                        + (v.getLocatario().getTelefone() == null ? "" : "Telefone: " + v.getLocatario().getTelefone() + "\n")
                        + (v.getMensagem() == null ? "" : "Mensagem: " + v.getMensagem() + "\n")
                        + "\nConfirme ou recuse em " + urlBase + "/inquilinos.html.");
    }

    public void visitaRespondida(Visita v) {
        boolean confirmada = v.getStatus() == StatusVisita.CONFIRMADA;
        enviar(v.getLocatario().getEmail(),
                "AlugaFacil - visita " + (confirmada ? "confirmada" : "nao confirmada") + ": " + v.getImovel().getTitulo(),
                "Ola, " + v.getLocatario().getNome() + "!\n\n"
                        + (confirmada
                        ? "O proprietario confirmou sua visita a " + v.getImovel().getTitulo() + " em "
                        + DATA_E_HORA.format(v.getDataHora()) + "."
                        : "O proprietario nao pode receber sua visita a " + v.getImovel().getTitulo() + " em "
                        + DATA_E_HORA.format(v.getDataHora()) + ". Voce pode pedir outro horario na pagina do imovel.")
                        + "\n\n" + urlBase + "/imovel.html?id=" + v.getImovel().getId());
    }

    // ----- mensagem de um interessado ao proprietario

    public void mensagemDeInteressado(Imovel imovel, Usuario de, String mensagem) {
        Usuario locador = imovel.getLocador();
        enviar(locador.getEmail(), "AlugaFacil - mensagem sobre " + imovel.getTitulo(),
                "Ola, " + locador.getNome() + "!\n\n"
                        + de.getNome() + " escreveu sobre o imovel " + imovel.getTitulo() + ":\n\n" + mensagem + "\n\n"
                        + "Responda para " + de.getEmail()
                        + (de.getTelefone() == null ? "" : " ou pelo telefone " + de.getTelefone()) + ".");
    }

    // ----- avaliacao, pagamento e manutencao

    public void avaliacaoRecebida(Avaliacao a) {
        Usuario locador = a.getImovel().getLocador();
        enviar(locador.getEmail(), "AlugaFacil - nova avaliacao de " + a.getImovel().getTitulo(),
                "Ola, " + locador.getNome() + "!\n\n"
                        + a.getUsuario().getNome() + " avaliou o imovel " + a.getImovel().getTitulo()
                        + " com nota " + a.getNota() + " de 5.\n"
                        + (a.getComentario() == null ? "" : "\"" + a.getComentario() + "\"\n")
                        + "\nVeja em " + urlBase + "/imovel.html?id=" + a.getImovel().getId() + "#avaliacoes.");
    }

    public void pagamentoRecusado(Pagamento p) {
        Usuario locador = p.getContrato().getImovel().getLocador();
        enviar(locador.getEmail(), "AlugaFacil - pagamento recusado: " + p.getContrato().getImovel().getTitulo(),
                "Ola, " + locador.getNome() + "!\n\n"
                        + "O pagamento do aluguel de " + mesExibido(p.getReferenciaMes()) + " do imovel "
                        + p.getContrato().getImovel().getTitulo() + ", feito por " + p.getContrato().getLocatario().getNome()
                        + ", foi recusado" + (p.getStatusDetalhe() == null ? "" : " (" + p.getStatusDetalhe() + ")") + ".\n"
                        + "Valor: " + moeda(p.getValor()) + "\n\n"
                        + "O locatario pode tentar de novo. Acompanhe em " + urlBase + "/financeiro.html.");
    }

    public void manutencaoSolicitada(Manutencao m) {
        Usuario locador = m.getContrato().getImovel().getLocador();
        enviar(locador.getEmail(), "AlugaFacil - nova solicitacao de manutencao: " + m.getContrato().getImovel().getTitulo(),
                "Ola, " + locador.getNome() + "!\n\n"
                        + m.getContrato().getLocatario().getNome() + " pediu manutencao (" + m.getTipo() + ", urgencia "
                        + m.getUrgencia() + "):\n\n" + m.getDescricao() + "\n\n"
                        + "Atualize o andamento em " + urlBase + "/proprietario.html.");
    }

    public void manutencaoAtualizada(Manutencao m) {
        Usuario locatario = m.getContrato().getLocatario();
        enviar(locatario.getEmail(), "AlugaFacil - sua manutencao esta " + rotuloDaManutencao(m),
                "Ola, " + locatario.getNome() + "!\n\n"
                        + "Sua solicitacao de manutencao (" + m.getTipo() + ") no imovel " + m.getContrato().getImovel().getTitulo()
                        + " agora esta: " + rotuloDaManutencao(m) + ".\n\n"
                        + "Acompanhe em " + urlBase + "/alugueis.html.");
    }

    // ----- apoio

    private static String rotuloDaManutencao(Manutencao m) {
        return switch (m.getStatus()) {
            case ABERTA -> "aberta";
            case EM_ANDAMENTO -> "em andamento";
            case CONCLUIDA -> "concluida";
        };
    }

    private static BigDecimal valorMensal(Imovel imovel) {
        return imovel.getValorAluguel().add(imovel.getValorCondominio() == null ? BigDecimal.ZERO : imovel.getValorCondominio());
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor).replace(' ', ' ');
    }

    // "2026-12" vira "12/2026".
    private static String mesExibido(String referenciaMes) {
        String[] partes = referenciaMes.split("-");
        return partes.length == 2 ? partes[1] + "/" + partes[0] : referenciaMes;
    }

    private void enviar(String para, String assunto, String texto) {
        try {
            envioDeEmail.enviar(para, assunto, texto);
        } catch (RuntimeException e) {
            log.error("Nao foi possivel enviar o aviso '{}' para {}", assunto, para, e);
        }
    }
}
