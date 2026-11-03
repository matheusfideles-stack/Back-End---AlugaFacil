package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroVisita;
import com.larenegocios.alugafacil.dto.DadosVisita;
import com.larenegocios.alugafacil.service.VisitaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class VisitaController {

    @Autowired
    private VisitaService visitaService;

    @PostMapping("/imoveis/{imovelId}/visitas")
    public ResponseEntity<DadosVisita> solicitar(@PathVariable Long imovelId,
                                                 @RequestBody @Valid DadosCadastroVisita dados,
                                                 @AuthenticationPrincipal Usuario locatario) {
        return ResponseEntity.ok(new DadosVisita(visitaService.solicitar(imovelId, dados, locatario)));
    }

    @GetMapping("/visitas/minhas")
    public ResponseEntity<Page<DadosVisita>> minhas(@AuthenticationPrincipal Usuario usuario, Pageable pageable) {
        return ResponseEntity.ok(visitaService.minhas(usuario.getId(), pageable).map(DadosVisita::new));
    }

    @GetMapping("/visitas/recebidas")
    public ResponseEntity<Page<DadosVisita>> recebidas(@AuthenticationPrincipal Usuario usuario, Pageable pageable) {
        return ResponseEntity.ok(visitaService.recebidas(usuario.getId(), pageable).map(DadosVisita::new));
    }

    @PutMapping("/visitas/{id}/confirmar")
    public ResponseEntity<DadosVisita> confirmar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new DadosVisita(visitaService.confirmar(id, usuario)));
    }

    @PutMapping("/visitas/{id}/recusar")
    public ResponseEntity<DadosVisita> recusar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new DadosVisita(visitaService.recusar(id, usuario)));
    }

    @PutMapping("/visitas/{id}/cancelar")
    public ResponseEntity<DadosVisita> cancelar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new DadosVisita(visitaService.cancelar(id, usuario)));
    }
}
