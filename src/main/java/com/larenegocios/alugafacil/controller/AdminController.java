package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.dto.DadosCadastroUsuario;
import com.larenegocios.alugafacil.dto.DadosUsuario;
import com.larenegocios.alugafacil.dto.DadosUsuarioCadastrado;
import com.larenegocios.alugafacil.dto.DadosSituacao;
import com.larenegocios.alugafacil.dto.DadosAvaliacaoAdmin;
import com.larenegocios.alugafacil.dto.DadosContrato;
import com.larenegocios.alugafacil.dto.DadosImovel;
import com.larenegocios.alugafacil.dto.DadosPagamento;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.service.AdministracaoService;
import com.larenegocios.alugafacil.service.AutenticacaoService;
import com.larenegocios.alugafacil.service.ConfirmacaoEmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AutenticacaoService autenticacaoService;

    @Autowired
    private ConfirmacaoEmailService confirmacaoEmailService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping("/locadores")
    public ResponseEntity<DadosUsuarioCadastrado> cadastrarLocador(@RequestBody @Valid DadosCadastroUsuario dados) {
        var locador = autenticacaoService.cadastrarLocador(dados);
        String codigoDeTeste = confirmacaoEmailService.enviarConfirmacao(locador).orElse(null);
        return ResponseEntity.ok(new DadosUsuarioCadastrado(locador, codigoDeTeste));
    }

    @GetMapping("/locadores")
    public ResponseEntity<Page<DadosUsuario>> listarLocadores(Pageable pageable) {
        return ResponseEntity.ok(usuarioRepository.findByPerfil(Perfil.LOCADOR, pageable).map(DadosUsuario::new));
    }

    @Autowired
    private AdministracaoService administracao;

    @GetMapping("/usuarios")
    public ResponseEntity<Page<DadosUsuario>> listarUsuarios(@RequestParam(required = false) Perfil perfil, Pageable pageable) {
        return ResponseEntity.ok(administracao.listarUsuarios(perfil, pageable));
    }

    @PutMapping("/usuarios/{id}/ativo")
    public ResponseEntity<Void> definirAtivoDoUsuario(@PathVariable Long id, @RequestBody @Valid DadosSituacao dados,
                                                      @AuthenticationPrincipal Usuario solicitante) {
        administracao.definirAtivoDoUsuario(id, dados.ativo(), solicitante);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/imoveis")
    public ResponseEntity<Page<DadosImovel>> listarImoveis(Pageable pageable) {
        return ResponseEntity.ok(administracao.listarImoveis(pageable));
    }

    @PutMapping("/imoveis/{id}/disponivel")
    public ResponseEntity<Void> definirDisponibilidade(@PathVariable Long id, @RequestBody @Valid DadosSituacao dados,
                                                       @AuthenticationPrincipal Usuario solicitante) {
        administracao.definirDisponibilidadeDoImovel(id, dados.ativo(), solicitante);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/avaliacoes")
    public ResponseEntity<Page<DadosAvaliacaoAdmin>> listarAvaliacoes(Pageable pageable) {
        return ResponseEntity.ok(administracao.listarAvaliacoes(pageable));
    }

    @GetMapping("/contratos")
    public ResponseEntity<Page<DadosContrato>> listarContratos(Pageable pageable) {
        return ResponseEntity.ok(administracao.listarContratos(pageable));
    }

    @PostMapping("/pagamentos/{id}/lembrete")
    public ResponseEntity<Void> enviarLembrete(@PathVariable Long id) {
        administracao.enviarLembreteDePagamento(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pagamentos")
    public ResponseEntity<Page<DadosPagamento>> listarPagamentos(Pageable pageable) {
        return ResponseEntity.ok(administracao.listarPagamentos(pageable));
    }
}
