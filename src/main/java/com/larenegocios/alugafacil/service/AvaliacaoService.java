package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.avaliacao.Avaliacao;
import com.larenegocios.alugafacil.domain.avaliacao.AvaliacaoRepository;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroAvaliacao;
import com.larenegocios.alugafacil.dto.DadosImovel;
import com.larenegocios.alugafacil.dto.DadosResumoAvaliacoes;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AvaliacaoService {

    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    @Autowired
    private ImovelService imovelService;

    public Page<Avaliacao> listar(Long imovelId, Pageable pageable) {
        imovelService.buscarPorId(imovelId);
        return avaliacaoRepository.findByImovelId(imovelId, pageable);
    }

    public DadosResumoAvaliacoes resumo(Long imovelId) {
        imovelService.buscarPorId(imovelId);
        long[] distribuicao = new long[6];
        long total = 0;
        long soma = 0;
        for (var contagem : avaliacaoRepository.contagemPorNota(imovelId)) {
            distribuicao[contagem.getNota()] = contagem.getTotal();
            total += contagem.getTotal();
            soma += (long) contagem.getNota() * contagem.getTotal();
        }
        return new DadosResumoAvaliacoes(total == 0 ? null : arredondar(soma / (double) total), total, distribuicao);
    }

    public Avaliacao minha(Long imovelId, Usuario usuario) {
        return avaliacaoRepository.findByImovelIdAndUsuarioId(imovelId, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Voce ainda nao avaliou este imovel"));
    }

    // Uma avaliacao por pessoa e por imovel: avaliar de novo atualiza a anterior.
    @Transactional
    public Avaliacao salvar(Long imovelId, DadosCadastroAvaliacao dados, Usuario autor) {
        Imovel imovel = imovelService.buscarPorId(imovelId);
        if (autor.getPerfil() != Perfil.LOCATARIO) {
            throw new RegraDeNegocioException("Somente locatarios podem avaliar imoveis");
        }
        Avaliacao avaliacao = avaliacaoRepository.findByImovelIdAndUsuarioId(imovelId, autor.getId())
                .orElseGet(() -> {
                    Avaliacao nova = new Avaliacao();
                    nova.setImovel(imovel);
                    nova.setUsuario(autor);
                    return nova;
                });
        avaliacao.setNota(dados.nota());
        String comentario = dados.comentario() == null ? "" : dados.comentario().trim();
        avaliacao.setComentario(comentario.isEmpty() ? null : comentario);
        avaliacao.setAtualizadoEm(LocalDateTime.now());
        return avaliacaoRepository.save(avaliacao);
    }

    @Transactional
    public void removerMinha(Long imovelId, Usuario autor) {
        avaliacaoRepository.delete(minha(imovelId, autor));
    }

    @Transactional
    public void removerPorId(Long id) {
        if (!avaliacaoRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Avaliacao nao encontrada");
        }
        avaliacaoRepository.deleteById(id);
    }

    public DadosImovel comResumo(Imovel imovel) {
        return comResumos(List.of(imovel)).get(0);
    }

    public Page<DadosImovel> comResumos(Page<Imovel> imoveis) {
        List<DadosImovel> dados = comResumos(imoveis.getContent());
        return new PageImpl<>(dados, imoveis.getPageable(), imoveis.getTotalElements());
    }

    private List<DadosImovel> comResumos(List<Imovel> imoveis) {
        if (imoveis.isEmpty()) {
            return List.of();
        }
        List<Long> ids = imoveis.stream().map(Imovel::getId).toList();
        Map<Long, AvaliacaoRepository.ResumoPorImovel> resumos = avaliacaoRepository.resumoPorImoveis(ids).stream()
                .collect(Collectors.toMap(AvaliacaoRepository.ResumoPorImovel::getImovelId, Function.identity()));
        return imoveis.stream().map(imovel -> {
            var resumo = resumos.get(imovel.getId());
            return resumo == null
                    ? new DadosImovel(imovel)
                    : new DadosImovel(imovel, arredondar(resumo.getMedia()), resumo.getTotal());
        }).toList();
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
