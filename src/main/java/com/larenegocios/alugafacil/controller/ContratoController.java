package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroContrato;
import com.larenegocios.alugafacil.dto.DadosContrato;
import com.larenegocios.alugafacil.service.ContratoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contratos")
public class ContratoController {

    @Autowired
    private ContratoService contratoService;

    @PostMapping
    public ResponseEntity<DadosContrato> firmar(@RequestBody @Valid DadosCadastroContrato dados,
                                                 @AuthenticationPrincipal Usuario locatario) {
        Contrato contrato = contratoService.firmar(dados, locatario);
        return ResponseEntity.ok(new DadosContrato(contrato));
    }

    @GetMapping("/meus")
    public ResponseEntity<Page<DadosContrato>> meusContratos(@AuthenticationPrincipal Usuario usuario,
                                                               @RequestParam(defaultValue = "locatario") String papel,
                                                               Pageable pageable) {
        Page<Contrato> contratos = "locador".equalsIgnoreCase(papel)
                ? contratoService.listarDoLocador(usuario.getId(), pageable)
                : contratoService.listarDoLocatario(usuario.getId(), pageable);

        return ResponseEntity.ok(contratos.map(DadosContrato::new));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DadosContrato> detalhar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Contrato contrato = contratoService.buscarPorId(id);
        contratoService.validarParticipante(contrato, usuario);
        return ResponseEntity.ok(new DadosContrato(contrato));
    }

    @PutMapping("/{id}/encerrar")
    public ResponseEntity<Void> encerrar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        contratoService.encerrar(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
