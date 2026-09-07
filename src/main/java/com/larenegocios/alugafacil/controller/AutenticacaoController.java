package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosAutenticacao;
import com.larenegocios.alugafacil.dto.DadosCadastroUsuario;
import com.larenegocios.alugafacil.dto.DadosTokenJWT;
import com.larenegocios.alugafacil.dto.DadosUsuario;
import com.larenegocios.alugafacil.infra.security.TokenService;
import com.larenegocios.alugafacil.service.AutenticacaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AutenticacaoController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AutenticacaoService autenticacaoService;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/registro")
    public ResponseEntity<DadosUsuario> registrar(@RequestBody @Valid DadosCadastroUsuario dados) {
        Usuario usuario = autenticacaoService.cadastrar(dados);
        return ResponseEntity.ok(new DadosUsuario(usuario));
    }

    @PostMapping("/login")
    public ResponseEntity<DadosTokenJWT> login(@RequestBody @Valid DadosAutenticacao dados) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(dados.email(), dados.senha());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        String tokenJWT = tokenService.gerarToken((Usuario) authentication.getPrincipal());

        return ResponseEntity.ok(new DadosTokenJWT(tokenJWT));
    }
}
