package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroImovel;
import com.larenegocios.alugafacil.dto.DadosImovel;
import com.larenegocios.alugafacil.service.ImovelService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/imoveis")
public class ImovelController {

    @Autowired
    private ImovelService imovelService;

    @PostMapping
    public ResponseEntity<DadosImovel> cadastrar(@RequestBody @Valid DadosCadastroImovel dados,
                                                  @AuthenticationPrincipal Usuario locador) {
        Imovel imovel = imovelService.cadastrar(dados, locador);
        return ResponseEntity.ok(new DadosImovel(imovel));
    }

    @GetMapping
    public ResponseEntity<Page<DadosImovel>> listar(@RequestParam(required = false) String cidade,
                                                      Pageable pageable) {
        Page<Imovel> imoveis = (cidade != null)
                ? imovelService.listarPorCidade(cidade, pageable)
                : imovelService.listarDisponiveis(pageable);

        return ResponseEntity.ok(imoveis.map(DadosImovel::new));
    }

    @GetMapping("/meus")
    public ResponseEntity<Page<DadosImovel>> meusImoveis(@AuthenticationPrincipal Usuario locador,
                                                           Pageable pageable) {
        Page<Imovel> imoveis = imovelService.listarDoLocador(locador.getId(), pageable);
        return ResponseEntity.ok(imoveis.map(DadosImovel::new));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DadosImovel> detalhar(@PathVariable Long id) {
        return ResponseEntity.ok(new DadosImovel(imovelService.buscarPorId(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id, @AuthenticationPrincipal Usuario solicitante) {
        imovelService.desativar(id, solicitante);
        return ResponseEntity.noContent().build();
    }
}
