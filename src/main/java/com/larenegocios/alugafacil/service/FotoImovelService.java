package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.FotoImovel;
import com.larenegocios.alugafacil.domain.imovel.FotoImovelRepository;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.storage.ArmazenamentoDeFotos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FotoImovelService {

    public static final int MAXIMO_POR_IMOVEL = 10;

    @Autowired
    private FotoImovelRepository fotoRepository;

    @Autowired
    private ImovelService imovelService;

    @Autowired
    private ArmazenamentoDeFotos armazenamento;

    public List<FotoImovel> listar(Long imovelId) {
        imovelService.buscarPorId(imovelId);
        return fotoRepository.findByImovelIdOrderByOrdemAscIdAsc(imovelId);
    }

    public Optional<FotoImovel> capa(Long imovelId) {
        return fotoRepository.findFirstByImovelIdOrderByOrdemAscIdAsc(imovelId);
    }

    public FotoImovel buscar(Long imovelId, Long fotoId) {
        return fotoRepository.findByIdAndImovelId(fotoId, imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto nao encontrada"));
    }

    @Transactional
    public List<FotoImovel> adicionar(Long imovelId, List<MultipartFile> arquivos, Usuario solicitante) {
        Imovel imovel = imovelService.buscarPorId(imovelId);
        imovelService.validarPropriedade(imovel, solicitante);

        List<MultipartFile> enviados = arquivos == null ? List.of() : arquivos.stream().filter(a -> !a.isEmpty()).toList();
        if (enviados.isEmpty()) {
            throw new RegraDeNegocioException("Selecione ao menos uma foto");
        }
        if (fotoRepository.countByImovelId(imovelId) + enviados.size() > MAXIMO_POR_IMOVEL) {
            throw new RegraDeNegocioException("Cada imovel pode ter no maximo " + MAXIMO_POR_IMOVEL + " fotos");
        }

        List<String> gravados = new ArrayList<>();
        List<FotoImovel> salvas = new ArrayList<>();
        try {
            int ordem = fotoRepository.maiorOrdem(imovelId) + 1;
            for (MultipartFile arquivo : enviados) {
                var guardada = armazenamento.salvar(arquivo);
                gravados.add(guardada.arquivo());

                FotoImovel foto = new FotoImovel();
                foto.setImovel(imovel);
                foto.setArquivo(guardada.arquivo());
                foto.setContentType(guardada.contentType());
                foto.setTamanho(guardada.tamanho());
                foto.setOrdem(ordem++);
                salvas.add(fotoRepository.save(foto));
            }
        } catch (RuntimeException e) {
            gravados.forEach(armazenamento::remover);
            throw e;
        }
        return salvas;
    }

    @Transactional
    public void definirCapa(Long imovelId, Long fotoId, Usuario solicitante) {
        FotoImovel foto = buscarComPermissao(imovelId, fotoId, solicitante);
        foto.setOrdem(fotoRepository.menorOrdem(imovelId) - 1);
    }

    @Transactional
    public void remover(Long imovelId, Long fotoId, Usuario solicitante) {
        FotoImovel foto = buscarComPermissao(imovelId, fotoId, solicitante);
        fotoRepository.delete(foto);
        armazenamento.remover(foto.getArquivo());
    }

    private FotoImovel buscarComPermissao(Long imovelId, Long fotoId, Usuario solicitante) {
        imovelService.validarPropriedade(imovelService.buscarPorId(imovelId), solicitante);
        return buscar(imovelId, fotoId);
    }
}
