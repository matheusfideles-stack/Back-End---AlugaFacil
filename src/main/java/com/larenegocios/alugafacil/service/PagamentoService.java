package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGateway;
import com.larenegocios.alugafacil.domain.pagamento.gateway.RespostaCobranca;
import com.larenegocios.alugafacil.domain.pagamento.gateway.SolicitacaoCobranca;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosSolicitacaoPagamento;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class PagamentoService {

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private PaymentGateway paymentGateway;

    @Transactional
    public CobrancaGerada gerarCobranca(DadosSolicitacaoPagamento dados, Usuario solicitante) {
        Contrato contrato = contratoService.buscarPorId(dados.contratoId());
        contratoService.validarParticipante(contrato, solicitante);

        pagamentoRepository.findByContratoIdAndReferenciaMes(contrato.getId(), dados.referenciaMes())
                .ifPresent(p -> {
                    throw new RegraDeNegocioException("Ja existe uma cobranca para este mes de referencia");
                });

        Pagamento pagamento = new Pagamento();
        pagamento.setContrato(contrato);
        pagamento.setReferenciaMes(dados.referenciaMes());
        pagamento.setValor(contrato.getValorMensal());
        pagamento.setMetodo(dados.metodo());
        pagamento.setStatus(StatusPagamento.PROCESSANDO);
        pagamento.setVencimento(calcularVencimento(dados.referenciaMes(), contrato.getDiaVencimento()));
        pagamento = pagamentoRepository.save(pagamento);

        SolicitacaoCobranca solicitacaoCobranca = new SolicitacaoCobranca(
                pagamento.getId(),
                pagamento.getValor(),
                pagamento.getMetodo(),
                "Aluguel " + contrato.getImovel().getTitulo() + " - " + dados.referenciaMes(),
                contrato.getLocatario().getEmail(),
                contrato.getLocatario().getCpf()
        );

        RespostaCobranca resposta = paymentGateway.criarCobranca(solicitacaoCobranca);

        pagamento.setIdTransacaoGateway(resposta.idTransacaoGateway());
        if ("approved".equalsIgnoreCase(resposta.status())) {
            pagamento.setStatus(StatusPagamento.PAGO);
            pagamento.setPagoEm(LocalDateTime.now());
        } else {
            pagamento.setStatus(StatusPagamento.PENDENTE);
        }

        return new CobrancaGerada(pagamento, resposta.linkPagamento());
    }

    public Pagamento buscarPorId(Long id) {
        return pagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento nao encontrado"));
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
    }

    private LocalDate calcularVencimento(String referenciaMes, Integer diaVencimento) {
        String[] partes = referenciaMes.split("-");
        int ano = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);
        return LocalDate.of(ano, mes, diaVencimento);
    }
}
