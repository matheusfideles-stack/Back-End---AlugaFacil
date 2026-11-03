package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroImovel;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.geocodificacao.Geocodificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;

@Service
public class ImovelService {

    private static final Logger log = LoggerFactory.getLogger(ImovelService.class);

    @Autowired
    private ImovelRepository imovelRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private Geocodificador geocodificador;

    public Imovel cadastrar(DadosCadastroImovel dados, Usuario locador) {
        Imovel imovel = new Imovel();
        imovel.setLocador(locador);
        imovel.setDisponivel(true);
        preencher(imovel, dados);
        localizar(imovel);

        return imovelRepository.save(imovel);
    }

    @Transactional
    public Imovel atualizar(Long id, DadosCadastroImovel dados, Usuario solicitante) {
        Imovel imovel = buscarPorId(id);
        validarPropriedade(imovel, solicitante);
        String enderecoAntes = enderecoParaBusca(imovel);
        preencher(imovel, dados);
        // So consulta de novo quando o endereco mudou (ou nunca foi localizado): mudar o preco nao gasta consulta.
        if (!enderecoAntes.equals(enderecoParaBusca(imovel)) || imovel.getLatitude() == null) {
            localizar(imovel);
        }
        return imovel;
    }

    // Usado pela rotina que completa a posicao dos imoveis antigos, um de cada vez.
    @Transactional
    public void localizarPendente(Long id) {
        imovelRepository.findById(id).filter(i -> i.getLatitude() == null).ifPresent(this::localizar);
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

    @Transactional
    public void desativar(Long id, Usuario solicitante) {
        Imovel imovel = buscarPorId(id);
        validarPropriedade(imovel, solicitante);
        imovel.setDisponivel(false);
    }

    @Transactional
    public void reativar(Long id, Usuario solicitante) {
        Imovel imovel = buscarPorId(id);
        validarPropriedade(imovel, solicitante);
        if (contratoRepository.existsByImovelIdAndStatus(id, StatusContrato.ATIVO)) {
            throw new RegraDeNegocioException("Este imovel possui um contrato ativo");
        }
        imovel.setDisponivel(true);
    }

    public void validarPropriedade(Imovel imovel, Usuario solicitante) {
        boolean ehDono = imovel.getLocador().getId().equals(solicitante.getId());
        boolean ehAdmin = solicitante.getPerfil().name().equals("ADMIN");
        if (!ehDono && !ehAdmin) {
            throw new RegraDeNegocioException("Voce nao tem permissao sobre este imovel");
        }
    }

    private void preencher(Imovel imovel, DadosCadastroImovel dados) {
        imovel.setTitulo(dados.titulo().trim());
        imovel.setDescricao(dados.descricao());
        imovel.setTipo(dados.tipo());
        imovel.setLogradouro(dados.logradouro().trim());
        imovel.setNumero(dados.numero().trim());
        imovel.setComplemento(dados.complemento());
        imovel.setBairro(dados.bairro().trim());
        imovel.setCidade(dados.cidade().trim());
        imovel.setUf(dados.uf().trim().toUpperCase());
        imovel.setCep(dados.cep().trim());
        imovel.setValorAluguel(dados.valorAluguel());
        imovel.setValorCondominio(dados.valorCondominio() != null ? dados.valorCondominio() : BigDecimal.ZERO);
        imovel.setQuartos(dados.quartos());
        imovel.setBanheiros(dados.banheiros());
        imovel.setVagas(dados.vagas());
        imovel.setAreaM2(dados.areaM2());
        // Quem nao manda o campo (clientes antigos) nao apaga as comodidades que o imovel ja tinha.
        if (dados.comodidades() != null) {
            imovel.setComodidades(new LinkedHashSet<>(dados.comodidades()));
        }
    }

    private static String enderecoParaBusca(Imovel imovel) {
        return String.join("|", String.valueOf(imovel.getLogradouro()), String.valueOf(imovel.getNumero()),
                String.valueOf(imovel.getBairro()), String.valueOf(imovel.getCidade()), String.valueOf(imovel.getUf()));
    }

    // Falha ao localizar nunca impede salvar o imovel. Endereco que mudou e nao foi achado perde a posicao antiga,
    // que estaria errada.
    private void localizar(Imovel imovel) {
        try {
            var coordenadas = geocodificador.localizar(imovel.getLogradouro(), imovel.getNumero(), imovel.getBairro(),
                    imovel.getCidade(), imovel.getUf());
            imovel.setLatitude(coordenadas.map(c -> c.latitude()).orElse(null));
            imovel.setLongitude(coordenadas.map(c -> c.longitude()).orElse(null));
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel localizar o imovel {} no mapa", imovel.getId(), e);
            imovel.setLatitude(null);
            imovel.setLongitude(null);
        }
    }
}
