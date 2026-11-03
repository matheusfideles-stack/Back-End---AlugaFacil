package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.imovel.FotoImovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosFoto;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.storage.ArmazenamentoDeFotos;
import com.larenegocios.alugafacil.service.FotoImovelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/imoveis/{imovelId}")
public class FotoImovelController {

    @Autowired
    private FotoImovelService fotoService;

    @Autowired
    private ArmazenamentoDeFotos armazenamento;

    @GetMapping("/fotos")
    public ResponseEntity<List<DadosFoto>> listar(@PathVariable Long imovelId) {
        return ResponseEntity.ok(paraDados(fotoService.listar(imovelId)));
    }

    @PostMapping(value = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DadosFoto>> adicionar(@PathVariable Long imovelId,
                                                      @RequestParam("fotos") List<MultipartFile> fotos,
                                                      @AuthenticationPrincipal Usuario solicitante) {
        fotoService.adicionar(imovelId, fotos, solicitante);
        return ResponseEntity.ok(paraDados(fotoService.listar(imovelId)));
    }

    @GetMapping("/fotos/{fotoId}/arquivo")
    public ResponseEntity<Resource> arquivo(@PathVariable Long imovelId, @PathVariable Long fotoId) {
        FotoImovel foto = fotoService.buscar(imovelId, fotoId);
        Resource conteudo = armazenamento.ler(foto.getArquivo());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(conteudo);
    }

    @GetMapping("/capa")
    public ResponseEntity<Void> capa(@PathVariable Long imovelId) {
        FotoImovel capa = fotoService.capa(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Este imovel ainda nao tem fotos"));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(new DadosFoto(capa, true).url()))
                .cacheControl(CacheControl.noCache())
                .build();
    }

    @PutMapping("/fotos/{fotoId}/capa")
    public ResponseEntity<Void> definirCapa(@PathVariable Long imovelId, @PathVariable Long fotoId,
                                             @AuthenticationPrincipal Usuario solicitante) {
        fotoService.definirCapa(imovelId, fotoId, solicitante);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/fotos/{fotoId}")
    public ResponseEntity<Void> remover(@PathVariable Long imovelId, @PathVariable Long fotoId,
                                         @AuthenticationPrincipal Usuario solicitante) {
        fotoService.remover(imovelId, fotoId, solicitante);
        return ResponseEntity.noContent().build();
    }

    private List<DadosFoto> paraDados(List<FotoImovel> fotos) {
        List<DadosFoto> dados = new ArrayList<>();
        for (int i = 0; i < fotos.size(); i++) {
            dados.add(new DadosFoto(fotos.get(i), i == 0));
        }
        return dados;
    }
}
