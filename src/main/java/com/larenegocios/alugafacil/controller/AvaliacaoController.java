package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosAvaliacao;
import com.larenegocios.alugafacil.dto.DadosCadastroAvaliacao;
import com.larenegocios.alugafacil.dto.DadosResumoAvaliacoes;
import com.larenegocios.alugafacil.service.AvaliacaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AvaliacaoController {

    @Autowired
    private AvaliacaoService avaliacaoService;

    @GetMapping("/imoveis/{imovelId}/avaliacoes")
    public ResponseEntity<Page<DadosAvaliacao>> listar(
            @PathVariable Long imovelId,
            @PageableDefault(size = 10, sort = "atualizadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(avaliacaoService.listar(imovelId, pageable).map(DadosAvaliacao::new));
    }

    @GetMapping("/imoveis/{imovelId}/avaliacoes/resumo")
    public ResponseEntity<DadosResumoAvaliacoes> resumo(@PathVariable Long imovelId) {
        return ResponseEntity.ok(avaliacaoService.resumo(imovelId));
    }

    @GetMapping("/imoveis/{imovelId}/avaliacoes/minha")
    public ResponseEntity<DadosAvaliacao> minha(@PathVariable Long imovelId, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new DadosAvaliacao(avaliacaoService.minha(imovelId, usuario)));
    }

    @PutMapping("/imoveis/{imovelId}/avaliacoes/minha")
    public ResponseEntity<DadosAvaliacao> avaliar(@PathVariable Long imovelId,
                                                   @RequestBody @Valid DadosCadastroAvaliacao dados,
                                                   @AuthenticationPrincipal Usuario autor) {
        return ResponseEntity.ok(new DadosAvaliacao(avaliacaoService.salvar(imovelId, dados, autor)));
    }

    @DeleteMapping("/imoveis/{imovelId}/avaliacoes/minha")
    public ResponseEntity<Void> removerMinha(@PathVariable Long imovelId, @AuthenticationPrincipal Usuario autor) {
        avaliacaoService.removerMinha(imovelId, autor);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/avaliacoes/{id}")
    public ResponseEntity<Void> removerPorId(@PathVariable Long id) {
        avaliacaoService.removerPorId(id);
        return ResponseEntity.noContent().build();
    }
}
