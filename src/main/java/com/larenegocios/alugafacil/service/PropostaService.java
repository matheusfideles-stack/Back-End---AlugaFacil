package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.proposta.Proposta;
import com.larenegocios.alugafacil.domain.proposta.PropostaRepository;
import com.larenegocios.alugafacil.domain.proposta.StatusProposta;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroContrato;
import com.larenegocios.alugafacil.dto.DadosCadastroProposta;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// O locatario pede, o proprietario decide. O contrato so e firmado quando a proposta e aceita.
@Service
public class PropostaService {

    @Autowired
    private PropostaRepository propostaRepository;

    @Autowired
    private ImovelService imovelService;

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private NotificacaoDeEventos notificador;

    @Transactional
    public Proposta criar(DadosCadastroProposta dados, Usuario locatario) {
        Imovel imovel = imovelService.buscarPorId(dados.imovelId());

        if (!imovel.getDisponivel()) {
            throw new RegraDeNegocioException("Este imovel nao esta disponivel para locacao");
        }
        if (imovel.getLocador().getId().equals(locatario.getId())) {
            throw new RegraDeNegocioException("Voce nao pode alugar o proprio imovel");
        }
        if (contratoRepository.existsByImovelIdAndStatus(imovel.getId(), StatusContrato.ATIVO)) {
            throw new RegraDeNegocioException("Este imovel ja possui um contrato ativo");
        }
        if (!dados.dataFim().isAfter(dados.dataInicio())) {
            throw new RegraDeNegocioException("A data de fim deve ser posterior a data de inicio");
        }
        if (propostaRepository.existsByImovelIdAndLocatarioIdAndStatus(imovel.getId(), locatario.getId(), StatusProposta.PENDENTE)) {
            throw new RegraDeNegocioException("Voce ja tem uma proposta aguardando resposta para este imovel");
        }

        Proposta proposta = new Proposta();
        proposta.setImovel(imovel);
        proposta.setLocatario(locatario);
        proposta.setDataInicio(dados.dataInicio());
        proposta.setDataFim(dados.dataFim());
        proposta.setDiaVencimento(dados.diaVencimento());
        String mensagem = dados.mensagem() == null ? "" : dados.mensagem().trim();
        proposta.setMensagem(mensagem.isEmpty() ? null : mensagem);

        Proposta salva = propostaRepository.save(proposta);
        notificador.propostaRecebida(salva);
        return salva;
    }

    public Page<Proposta> minhas(Long locatarioId, Pageable pageable) {
        return propostaRepository.findByLocatarioId(locatarioId, pageable);
    }

    public Page<Proposta> recebidas(Long locadorId, Pageable pageable) {
        return propostaRepository.findByImovelLocadorId(locadorId, pageable);
    }

    public Proposta buscarPorId(Long id) {
        return propostaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Proposta nao encontrada"));
    }

    @Transactional
    public Proposta aceitar(Long id, Usuario solicitante) {
        Proposta proposta = buscarPendenteDoLocador(id, solicitante);
        LocalDateTime agora = LocalDateTime.now();

        // Firmar valida de novo se o imovel segue disponivel; se nao, a aceitacao inteira e desfeita.
        Contrato contrato = contratoService.firmar(new DadosCadastroContrato(proposta.getImovel().getId(), proposta.getDataInicio(),
                proposta.getDataFim(), proposta.getDiaVencimento()), proposta.getLocatario());

        proposta.setStatus(StatusProposta.ACEITA);
        proposta.setContrato(contrato);
        proposta.setRespondidoEm(agora);

        // As outras propostas do mesmo imovel perdem o objeto: ficam recusadas e os interessados sao avisados.
        for (Proposta outra : propostaRepository.findByImovelIdAndStatus(proposta.getImovel().getId(), StatusProposta.PENDENTE)) {
            if (!outra.getId().equals(proposta.getId())) {
                outra.setStatus(StatusProposta.RECUSADA);
                outra.setMotivoRecusa("O imovel foi alugado para outro interessado");
                outra.setRespondidoEm(agora);
                notificador.propostaRecusada(outra);
            }
        }
        notificador.propostaAceita(proposta);
        return proposta;
    }

    @Transactional
    public Proposta recusar(Long id, String motivo, Usuario solicitante) {
        Proposta proposta = buscarPendenteDoLocador(id, solicitante);
        String texto = motivo == null ? "" : motivo.trim();
        proposta.setStatus(StatusProposta.RECUSADA);
        proposta.setMotivoRecusa(texto.isEmpty() ? null : texto);
        proposta.setRespondidoEm(LocalDateTime.now());
        notificador.propostaRecusada(proposta);
        return proposta;
    }

    @Transactional
    public Proposta cancelar(Long id, Usuario solicitante) {
        Proposta proposta = buscarPorId(id);
        if (!proposta.getLocatario().getId().equals(solicitante.getId())) {
            throw new RegraDeNegocioException("Voce nao tem permissao sobre esta proposta");
        }
        exigirPendente(proposta);
        proposta.setStatus(StatusProposta.CANCELADA);
        proposta.setRespondidoEm(LocalDateTime.now());
        return proposta;
    }

    private Proposta buscarPendenteDoLocador(Long id, Usuario solicitante) {
        Proposta proposta = buscarPorId(id);
        imovelService.validarPropriedade(proposta.getImovel(), solicitante);
        exigirPendente(proposta);
        return proposta;
    }

    private void exigirPendente(Proposta proposta) {
        if (proposta.getStatus() != StatusProposta.PENDENTE) {
            throw new RegraDeNegocioException("Esta proposta ja foi respondida");
        }
    }
}
