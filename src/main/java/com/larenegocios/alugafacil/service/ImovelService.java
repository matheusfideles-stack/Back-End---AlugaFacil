package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroImovel;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ImovelService {

    @Autowired
    private ImovelRepository imovelRepository;

    public Imovel cadastrar(DadosCadastroImovel dados, Usuario locador) {
        Imovel imovel = new Imovel();
        imovel.setLocador(locador);
        imovel.setTitulo(dados.titulo());
        imovel.setDescricao(dados.descricao());
        imovel.setTipo(dados.tipo());
        imovel.setLogradouro(dados.logradouro());
        imovel.setNumero(dados.numero());
        imovel.setComplemento(dados.complemento());
        imovel.setBairro(dados.bairro());
        imovel.setCidade(dados.cidade());
        imovel.setUf(dados.uf().toUpperCase());
        imovel.setCep(dados.cep());
        imovel.setValorAluguel(dados.valorAluguel());
        imovel.setValorCondominio(dados.valorCondominio() != null ? dados.valorCondominio() : BigDecimal.ZERO);
        imovel.setDisponivel(true);

        return imovelRepository.save(imovel);
    }

    public Page<Imovel> listarDisponiveis(Pageable pageable) {
        return imovelRepository.findByDisponivelTrue(pageable);
    }

    public Page<Imovel> listarPorCidade(String cidade, Pageable pageable) {
        return imovelRepository.findByCidadeIgnoreCaseAndDisponivelTrue(cidade, pageable);
    }

    public Page<Imovel> listarDoLocador(Long locadorId, Pageable pageable) {
        return imovelRepository.findByLocadorId(locadorId, pageable);
    }

    public Imovel buscarPorId(Long id) {
        return imovelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imovel nao encontrado"));
    }

    public void desativar(Long id, Usuario solicitante) {
        Imovel imovel = buscarPorId(id);
        validarPropriedade(imovel, solicitante);
        imovel.setDisponivel(false);
    }

    public void validarPropriedade(Imovel imovel, Usuario solicitante) {
        boolean ehDono = imovel.getLocador().getId().equals(solicitante.getId());
        boolean ehAdmin = solicitante.getPerfil().name().equals("ADMIN");
        if (!ehDono && !ehAdmin) {
            throw new RegraDeNegocioException("Voce nao tem permissao sobre este imovel");
        }
    }
}
