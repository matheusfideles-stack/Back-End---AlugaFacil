package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.proposta.Proposta;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroProposta;
import com.larenegocios.alugafacil.dto.DadosProposta;
import com.larenegocios.alugafacil.dto.DadosRecusa;
import com.larenegocios.alugafacil.service.PropostaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/propostas")
public class PropostaController {

    @Autowired
    private PropostaService propostaService;

    @PostMapping
    public ResponseEntity<DadosProposta> criar(@RequestBody @Valid DadosCadastroProposta dados,
                                               @AuthenticationPrincipal Usuario locatario) {
        return ResponseEntity.ok(new DadosProposta(propostaService.criar(dados, locatario)));
    }

    @GetMapping("/minhas")
    public ResponseEntity<Page<DadosProposta>> minhas(@AuthenticationPrincipal Usuario usuario, Pageable pageable) {
        return ResponseEntity.ok(propostaService.minhas(usuario.getId(), pageable).map(DadosProposta::new));
    }

    @GetMapping("/recebidas")
    public ResponseEntity<Page<DadosProposta>> recebidas(@AuthenticationPrincipal Usuario usuario, Pageable pageable) {
        return ResponseEntity.ok(propostaService.recebidas(usuario.getId(), pageable).map(DadosProposta::new));
    }

    @PutMapping("/{id}/aceitar")
    public ResponseEntity<DadosProposta> aceitar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Proposta proposta = propostaService.aceitar(id, usuario);
        return ResponseEntity.ok(new DadosProposta(proposta));
    }

    @PutMapping("/{id}/recusar")
    public ResponseEntity<DadosProposta> recusar(@PathVariable Long id,
                                                 @RequestBody(required = false) @Valid DadosRecusa dados,
                                                 @AuthenticationPrincipal Usuario usuario) {
        Proposta proposta = propostaService.recusar(id, dados == null ? null : dados.motivo(), usuario);
        return ResponseEntity.ok(new DadosProposta(proposta));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<DadosProposta> cancelar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new DadosProposta(propostaService.cancelar(id, usuario)));
    }
}
