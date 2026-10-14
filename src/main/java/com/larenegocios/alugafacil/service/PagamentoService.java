package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.Pagador;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGateway;
import com.larenegocios.alugafacil.domain.pagamento.gateway.RespostaCobranca;
import com.larenegocios.alugafacil.domain.pagamento.gateway.SolicitacaoCobranca;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosSolicitacaoPagamento;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.Set;

@Service
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    /** Uma cobranca nestes estados nao pode ser refeita: ja foi paga ou esta em captura. */
    private static final Set<StatusPagamento> ESTADOS_BLOQUEADOS =
            EnumSet.of(StatusPagamento.PAGO, StatusPagamento.PROCESSANDO);

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private PaymentGateway paymentGateway;

    @Transactional
    public Pagamento gerarCobranca(DadosSolicitacaoPagamento dados, Usuario solicitante) {
        Contrato contrato = contratoService.buscarPorId(dados.contratoId());
        contratoService.validarParticipante(contrato, solicitante);
        validarDadosDoMetodo(dados);

        Pagamento pagamento = prepararPagamento(dados, contrato);

        SolicitacaoCobranca solicitacao = new SolicitacaoCobranca(
                pagamento.getId(),
                pagamento.getValor(),
                pagamento.getMetodo(),
                "Aluguel " + contrato.getImovel().getTitulo() + " - " + dados.referenciaMes(),
                montarPagador(contrato),
                pagamento.getVencimento(),
                dados.tokenCartao(),
                dados.parcelas(),
                dados.bandeiraCartao()
        );

        aplicarResposta(pagamento, paymentGateway.criarCobranca(solicitacao));
        return pagamento;
    }

    public Pagamento buscarPorId(Long id) {
        return pagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento nao encontrado"));
    }

    /**
     * Reconsulta o gateway e atualiza o status local. Pix e boleto sao confirmados de
     * forma assincrona, entao o webhook e o caminho normal e este metodo e a rede de
     * seguranca para quando a notificacao nao chega.
     */
    @Transactional
    public Pagamento sincronizarComGateway(Long id, Usuario solicitante) {
        Pagamento pagamento = buscarPorId(id);
        contratoService.validarParticipante(pagamento.getContrato(), solicitante);

        if (!StringUtils.hasText(pagamento.getIdTransacaoGateway())) {
            throw new RegraDeNegocioException("Este pagamento ainda nao possui uma cobranca no gateway");
        }

        aplicarResposta(pagamento, paymentGateway.consultarCobranca(pagamento.getIdTransacaoGateway()));
        return pagamento;
    }

    /**
     * Processa a notificacao do gateway. Nao recebe usuario: quem chama e o provedor.
     * O status vem sempre de uma consulta a API, nunca do corpo da notificacao.
     */
    @Transactional
    public void processarNotificacao(String idTransacaoGateway) {
        pagamentoRepository.findByIdTransacaoGateway(idTransacaoGateway).ifPresentOrElse(
                pagamento -> {
                    StatusPagamento anterior = pagamento.getStatus();
                    aplicarResposta(pagamento, paymentGateway.consultarCobranca(idTransacaoGateway));
                    log.info("Notificacao processada: pagamento={} transacao={} status {} -> {}",
                            pagamento.getId(), idTransacaoGateway, anterior, pagamento.getStatus());
                },
                () -> log.warn("Notificacao recebida para transacao desconhecida: {}", idTransacaoGateway));
    }

    public Page<Pagamento> listarDoContrato(Long contratoId, Usuario solicitante, Pageable pageable) {
        Contrato contrato = contratoService.buscarPorId(contratoId);
        contratoService.validarParticipante(contrato, solicitante);
        return pagamentoRepository.findByContratoId(contratoId, pageable);
    }

    public Page<Pagamento> listarDoLocatario(Long locatarioId, Pageable pageable) {
        return pagamentoRepository.findByContratoLocatarioId(locatarioId, pageable);
    }

    @Transactional
    public void estornar(Long id, Usuario solicitante) {
        Pagamento pagamento = buscarPorId(id);
        contratoService.validarParticipante(pagamento.getContrato(), solicitante);

        if (pagamento.getStatus() != StatusPagamento.PAGO) {
            throw new RegraDeNegocioException("Somente pagamentos confirmados podem ser estornados");
        }

        paymentGateway.estornarCobranca(pagamento.getIdTransacaoGateway());
        pagamento.setStatus(StatusPagamento.ESTORNADO);
        pagamento.setStatusDetalhe("refunded");
    }

    // ---------------------------------------------------------------- apoio

    private void validarDadosDoMetodo(DadosSolicitacaoPagamento dados) {
        if (!dados.metodo().ehCartao()) {
            return;
        }
        if (!StringUtils.hasText(dados.tokenCartao())) {
            throw new RegraDeNegocioException(
                    "Pagamento com cartao exige o campo tokenCartao, gerado no frontend com a public key do gateway");
        }
        if (!StringUtils.hasText(dados.bandeiraCartao())) {
            throw new RegraDeNegocioException(
                    "Pagamento com cartao exige o campo bandeiraCartao (ex.: master, visa, elo, debmaster)");
        }
        if (dados.metodo() == MetodoPagamento.CARTAO_DEBITO && dados.parcelas() != null && dados.parcelas() > 1) {
            throw new RegraDeNegocioException("Cartao de debito nao permite parcelamento");
        }
    }

    /**
     * Cria a cobranca do mes ou reaproveita a linha existente. A tabela tem uma restricao
     * unica por (contrato, mes de referencia), entao uma tentativa recusada precisa reusar
     * o mesmo registro - do contrario o locatario ficaria sem conseguir pagar o mes.
     */
    private Pagamento prepararPagamento(DadosSolicitacaoPagamento dados, Contrato contrato) {
        Pagamento pagamento = pagamentoRepository
                .findByContratoIdAndReferenciaMes(contrato.getId(), dados.referenciaMes())
                .orElseGet(Pagamento::new);

        if (pagamento.getId() != null && ESTADOS_BLOQUEADOS.contains(pagamento.getStatus())) {
            throw new RegraDeNegocioException(pagamento.getStatus() == StatusPagamento.PAGO
                    ? "Este mes de referencia ja esta pago"
                    : "Ja existe uma cobranca em processamento para este mes de referencia");
        }

        pagamento.setContrato(contrato);
        pagamento.setReferenciaMes(dados.referenciaMes());
        pagamento.setValor(contrato.getValorMensal());
        pagamento.setMetodo(dados.metodo());
        pagamento.setStatus(StatusPagamento.PROCESSANDO);
        pagamento.setVencimento(calcularVencimento(dados.referenciaMes(), contrato.getDiaVencimento()));

        // Limpa os dados da tentativa anterior antes de pedir uma nova cobranca.
        pagamento.setIdTransacaoGateway(null);
        pagamento.setStatusDetalhe(null);
        pagamento.setLinkPagamento(null);
        pagamento.setPixCopiaECola(null);
        pagamento.setLinhaDigitavel(null);
        pagamento.setExpiraEm(null);
        pagamento.setPagoEm(null);

        // save + flush para ter o id antes de chamar o gateway: ele vai como external_reference.
        return pagamentoRepository.saveAndFlush(pagamento);
    }

    private void aplicarResposta(Pagamento pagamento, RespostaCobranca resposta) {
        pagamento.setIdTransacaoGateway(resposta.idTransacaoGateway());
        pagamento.setStatus(resposta.status());
        pagamento.setStatusDetalhe(resposta.statusDetalhe());

        // A consulta de status nao repete os dados da cobranca; so sobrescreve o que veio preenchido.
        if (resposta.linkPagamento() != null) {
            pagamento.setLinkPagamento(resposta.linkPagamento());
        }
        if (resposta.pixCopiaECola() != null) {
            pagamento.setPixCopiaECola(resposta.pixCopiaECola());
        }
        if (resposta.linhaDigitavel() != null) {
            pagamento.setLinhaDigitavel(resposta.linhaDigitavel());
        }
        if (resposta.expiraEm() != null) {
            pagamento.setExpiraEm(resposta.expiraEm());
        }

        pagamento.setPixQrCodeBase64(resposta.pixQrCodeBase64());
        pagamento.setUrlAutenticacao3ds(resposta.urlAutenticacao3ds());

        if (resposta.status() == StatusPagamento.PAGO && pagamento.getPagoEm() == null) {
            pagamento.setPagoEm(LocalDateTime.now());
        }
    }

    private Pagador montarPagador(Contrato contrato) {
        Usuario locatario = contrato.getLocatario();
        Imovel imovel = contrato.getImovel();

        // O Mercado Pago separa nome e sobrenome; o cadastro guarda o nome completo em um campo so.
        String nomeCompleto = locatario.getNome() == null ? "" : locatario.getNome().trim();
        int separador = nomeCompleto.indexOf(' ');
        String nome = separador > 0 ? nomeCompleto.substring(0, separador) : nomeCompleto;
        String sobrenome = separador > 0 ? nomeCompleto.substring(separador + 1).trim() : nomeCompleto;

        // Endereco do pagador: usamos o do imovel locado, unico endereco conhecido do locatario.
        return new Pagador(
                nome,
                sobrenome,
                locatario.getEmail(),
                locatario.getCpf(),
                imovel.getCep(),
                imovel.getLogradouro(),
                imovel.getNumero(),
                imovel.getBairro(),
                imovel.getCidade(),
                imovel.getUf()
        );
    }

    private LocalDate calcularVencimento(String referenciaMes, Integer diaVencimento) {
        YearMonth mesReferencia = YearMonth.parse(referenciaMes);
        // Meses curtos: um vencimento no dia 31 vira o ultimo dia do mes.
        return mesReferencia.atDay(Math.min(diaVencimento, mesReferencia.lengthOfMonth()));
    }
}
