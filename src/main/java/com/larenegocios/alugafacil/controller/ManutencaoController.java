package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosAtualizacaoManutencao;
import com.larenegocios.alugafacil.dto.DadosCadastroManutencao;
import com.larenegocios.alugafacil.dto.DadosManutencao;
import com.larenegocios.alugafacil.service.ManutencaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/manutencoes")
public class ManutencaoController {

    @Autowired
    private ManutencaoService manutencaoService;

    @PostMapping
    public ResponseEntity<DadosManutencao> solicitar(@RequestBody @Valid DadosCadastroManutencao dados,
                                                     @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(new DadosManutencao(manutencaoService.solicitar(dados, solicitante)));
    }

    @GetMapping("/meus")
    public ResponseEntity<Page<DadosManutencao>> meus(@AuthenticationPrincipal Usuario solicitante, Pageable pageable) {
        return ResponseEntity.ok(manutencaoService.listarDoLocatario(solicitante.getId(), pageable)
                .map(DadosManutencao::new));
    }

    @GetMapping("/recebidas")
    public ResponseEntity<Page<DadosManutencao>> recebidas(@AuthenticationPrincipal Usuario solicitante,
                                                           Pageable pageable) {
        return ResponseEntity.ok(manutencaoService.listarDoLocador(solicitante.getId(), pageable)
                .map(DadosManutencao::new));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<DadosManutencao> atualizarStatus(@PathVariable Long id,
                                                           @RequestBody @Valid DadosAtualizacaoManutencao dados,
                                                           @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(new DadosManutencao(
                manutencaoService.atualizarStatus(id, dados.status(), solicitante)));
    }
}
