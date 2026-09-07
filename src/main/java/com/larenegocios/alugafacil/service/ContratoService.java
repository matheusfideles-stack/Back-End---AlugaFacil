package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroContrato;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContratoService {

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private ImovelService imovelService;

    @Transactional
    public Contrato firmar(DadosCadastroContrato dados, Usuario locatario) {
        Imovel imovel = imovelService.buscarPorId(dados.imovelId());

        if (!imovel.getDisponivel()) {
            throw new RegraDeNegocioException("Este imovel nao esta disponivel para locacao");
        }
        if (contratoRepository.existsByImovelIdAndStatus(imovel.getId(), StatusContrato.ATIVO)) {
            throw new RegraDeNegocioException("Este imovel ja possui um contrato ativo");
        }
        if (!dados.dataFim().isAfter(dados.dataInicio())) {
            throw new RegraDeNegocioException("A data de fim deve ser posterior a data de inicio");
        }

        Contrato contrato = new Contrato();
        contrato.setImovel(imovel);
        contrato.setLocatario(locatario);
        contrato.setDataInicio(dados.dataInicio());
        contrato.setDataFim(dados.dataFim());
        contrato.setDiaVencimento(dados.diaVencimento());
        contrato.setValorMensal(imovel.getValorAluguel().add(imovel.getValorCondominio()));
        contrato.setStatus(StatusContrato.ATIVO);

        imovel.setDisponivel(false);

        return contratoRepository.save(contrato);
    }

    public Contrato buscarPorId(Long id) {
        return contratoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato nao encontrado"));
    }

    public Page<Contrato> listarDoLocatario(Long locatarioId, Pageable pageable) {
        return contratoRepository.findByLocatarioId(locatarioId, pageable);
    }

    public Page<Contrato> listarDoLocador(Long locadorId, Pageable pageable) {
        return contratoRepository.findByImovelLocadorId(locadorId, pageable);
    }

    @Transactional
    public void encerrar(Long id, Usuario solicitante) {
        Contrato contrato = buscarPorId(id);
        validarParticipante(contrato, solicitante);
        contrato.setStatus(StatusContrato.ENCERRADO);
        contrato.getImovel().setDisponivel(true);
    }

    public void validarParticipante(Contrato contrato, Usuario solicitante) {
        boolean ehLocatario = contrato.getLocatario().getId().equals(solicitante.getId());
        boolean ehLocador = contrato.getImovel().getLocador().getId().equals(solicitante.getId());
        boolean ehAdmin = solicitante.getPerfil().name().equals("ADMIN");
        if (!ehLocatario && !ehLocador && !ehAdmin) {
            throw new RegraDeNegocioException("Voce nao tem permissao sobre este contrato");
        }
    }
}
