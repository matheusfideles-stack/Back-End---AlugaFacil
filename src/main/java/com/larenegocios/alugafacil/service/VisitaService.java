package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.visita.StatusVisita;
import com.larenegocios.alugafacil.domain.visita.Visita;
import com.larenegocios.alugafacil.domain.visita.VisitaRepository;
import com.larenegocios.alugafacil.dto.DadosCadastroVisita;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitaService {

    @Autowired
    private VisitaRepository visitaRepository;

    @Autowired
    private ImovelService imovelService;

    @Autowired
    private NotificacaoDeEventos notificador;

    @Transactional
    public Visita solicitar(Long imovelId, DadosCadastroVisita dados, Usuario locatario) {
        Imovel imovel = imovelService.buscarPorId(imovelId);
        if (!imovel.getDisponivel()) {
            throw new RegraDeNegocioException("Este imovel nao esta disponivel para visita");
        }
        if (imovel.getLocador().getId().equals(locatario.getId())) {
            throw new RegraDeNegocioException("Voce nao pode visitar o proprio imovel");
        }

        Visita visita = new Visita();
        visita.setImovel(imovel);
        visita.setLocatario(locatario);
        visita.setDataHora(dados.dataHora());
        String mensagem = dados.mensagem() == null ? "" : dados.mensagem().trim();
        visita.setMensagem(mensagem.isEmpty() ? null : mensagem);

        Visita salva = visitaRepository.save(visita);
        notificador.visitaSolicitada(salva);
        return salva;
    }

    public Page<Visita> minhas(Long locatarioId, Pageable pageable) {
        return visitaRepository.findByLocatarioId(locatarioId, pageable);
    }

    public Page<Visita> recebidas(Long locadorId, Pageable pageable) {
        return visitaRepository.findByImovelLocadorId(locadorId, pageable);
    }

    @Transactional
    public Visita confirmar(Long id, Usuario solicitante) {
        return responder(id, StatusVisita.CONFIRMADA, solicitante);
    }

    @Transactional
    public Visita recusar(Long id, Usuario solicitante) {
        return responder(id, StatusVisita.RECUSADA, solicitante);
    }

    @Transactional
    public Visita cancelar(Long id, Usuario solicitante) {
        Visita visita = buscarPorId(id);
        if (!visita.getLocatario().getId().equals(solicitante.getId())) {
            throw new RegraDeNegocioException("Voce nao tem permissao sobre esta visita");
        }
        if (visita.getStatus() == StatusVisita.RECUSADA || visita.getStatus() == StatusVisita.CANCELADA) {
            throw new RegraDeNegocioException("Esta visita nao pode mais ser cancelada");
        }
        visita.setStatus(StatusVisita.CANCELADA);
        return visita;
    }

    private Visita responder(Long id, StatusVisita novoStatus, Usuario solicitante) {
        Visita visita = buscarPorId(id);
        imovelService.validarPropriedade(visita.getImovel(), solicitante);
        if (visita.getStatus() != StatusVisita.SOLICITADA) {
            throw new RegraDeNegocioException("Este pedido de visita ja foi respondido");
        }
        visita.setStatus(novoStatus);
        notificador.visitaRespondida(visita);
        return visita;
    }

    private Visita buscarPorId(Long id) {
        return visitaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Visita nao encontrada"));
    }
}
