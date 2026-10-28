package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.dto.DadosCadastroUsuario;
import com.larenegocios.alugafacil.dto.DadosUsuario;
import com.larenegocios.alugafacil.dto.DadosUsuarioCadastrado;
import com.larenegocios.alugafacil.service.AutenticacaoService;
import com.larenegocios.alugafacil.service.ConfirmacaoEmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
