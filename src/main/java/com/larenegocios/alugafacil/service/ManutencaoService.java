package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.manutencao.Manutencao;
import com.larenegocios.alugafacil.domain.manutencao.ManutencaoRepository;
import com.larenegocios.alugafacil.domain.manutencao.StatusManutencao;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroManutencao;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManutencaoService {

    @Autowired
    private ManutencaoRepository manutencaoRepository;

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private NotificacaoDeEventos notificador;

    @Transactional
    public Manutencao solicitar(DadosCadastroManutencao dados, Usuario solicitante) {
        Contrato contrato = contratoService.buscarPorId(dados.contratoId());
        if (!contrato.getLocatario().getId().equals(solicitante.getId())) {
            throw new RegraDeNegocioException("Somente o locatario do contrato pode solicitar manutencao");
        }
        if (contrato.getStatus() != StatusContrato.ATIVO) {
            throw new RegraDeNegocioException("Este contrato nao esta ativo");
        }

        Manutencao manutencao = new Manutencao();
        manutencao.setContrato(contrato);
        manutencao.setTipo(dados.tipo().trim());
        manutencao.setUrgencia(dados.urgencia());
        manutencao.setDescricao(dados.descricao().trim());
        Manutencao salva = manutencaoRepository.save(manutencao);
        notificador.manutencaoSolicitada(salva);
        return salva;
    }

    public Page<Manutencao> listarDoLocatario(Long locatarioId, Pageable pageable) {
        return manutencaoRepository.findByContratoLocatarioId(locatarioId, pageable);
    }

    public Page<Manutencao> listarDoLocador(Long locadorId, Pageable pageable) {
        return manutencaoRepository.findByContratoImovelLocadorId(locadorId, pageable);
    }

    @Transactional
    public Manutencao atualizarStatus(Long id, StatusManutencao status, Usuario solicitante) {
        Manutencao manutencao = manutencaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitacao de manutencao nao encontrada"));

        boolean ehLocador = manutencao.getContrato().getImovel().getLocador().getId().equals(solicitante.getId());
        boolean ehAdmin = solicitante.getPerfil().name().equals("ADMIN");
        if (!ehLocador && !ehAdmin) {
            throw new RegraDeNegocioException("Voce nao tem permissao sobre esta solicitacao");
        }

        boolean mudou = manutencao.getStatus() != status;
        manutencao.setStatus(status);
        if (mudou) {
            notificador.manutencaoAtualizada(manutencao);
        }
        return manutencao;
    }
}
