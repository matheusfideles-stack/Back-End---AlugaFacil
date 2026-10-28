package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosAutenticacao;
import com.larenegocios.alugafacil.dto.DadosCadastroUsuario;
import com.larenegocios.alugafacil.dto.DadosConfirmarEmail;
import com.larenegocios.alugafacil.dto.DadosEsqueciSenha;
import com.larenegocios.alugafacil.dto.DadosMensagem;
import com.larenegocios.alugafacil.dto.DadosRedefinirSenha;
import com.larenegocios.alugafacil.dto.DadosRespostaEsqueciSenha;
import com.larenegocios.alugafacil.dto.DadosTokenJWT;
import com.larenegocios.alugafacil.dto.DadosUsuarioCadastrado;
import com.larenegocios.alugafacil.infra.exception.EmailNaoConfirmadoException;
import com.larenegocios.alugafacil.infra.security.TokenService;
import com.larenegocios.alugafacil.service.AutenticacaoService;
import com.larenegocios.alugafacil.service.ConfirmacaoEmailService;
import com.larenegocios.alugafacil.service.RecuperacaoSenhaService;
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
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Autowired
    private ConfirmacaoEmailService confirmacaoEmailService;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/registro")
    public ResponseEntity<DadosUsuarioCadastrado> registrar(@RequestBody @Valid DadosCadastroUsuario dados) {
        Usuario usuario = autenticacaoService.cadastrar(dados);
        String codigoDeTeste = confirmacaoEmailService.enviarConfirmacao(usuario).orElse(null);
        return ResponseEntity.ok(new DadosUsuarioCadastrado(usuario, codigoDeTeste));
    }

    @PostMapping("/login")
    public ResponseEntity<DadosTokenJWT> login(@RequestBody @Valid DadosAutenticacao dados) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(
                AutenticacaoService.normalizarEmail(dados.email()), dados.senha());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.getEmailConfirmado()) {
            throw new EmailNaoConfirmadoException(
                    "Confirme seu e-mail para entrar: enviamos um codigo de verificacao para " + usuario.getEmail());
        }
        String tokenJWT = tokenService.gerarToken(usuario);

        return ResponseEntity.ok(new DadosTokenJWT(tokenJWT, usuario.getNome(), usuario.getPerfil()));
    }

    @PostMapping("/confirmar-email")
    public ResponseEntity<DadosMensagem> confirmarEmail(@RequestBody @Valid DadosConfirmarEmail dados) {
        confirmacaoEmailService.confirmar(dados.email(), dados.codigo());
        return ResponseEntity.ok(new DadosMensagem("E-mail confirmado! Agora voce ja pode entrar."));
    }

    @PostMapping("/reenviar-confirmacao")
    public ResponseEntity<DadosRespostaEsqueciSenha> reenviarConfirmacao(@RequestBody @Valid DadosEsqueciSenha dados) {
        String codigoDeTeste = confirmacaoEmailService.reenviar(dados.email()).orElse(null);
        return ResponseEntity.ok(new DadosRespostaEsqueciSenha(
                "Se existir uma conta aguardando confirmacao com esse e-mail, enviamos um novo codigo.", codigoDeTeste));
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<DadosRespostaEsqueciSenha> esqueciSenha(@RequestBody @Valid DadosEsqueciSenha dados) {
        String codigoDeTeste = recuperacaoSenhaService.solicitar(dados.email()).orElse(null);
        return ResponseEntity.ok(new DadosRespostaEsqueciSenha(
                "Se o email estiver cadastrado, voce vai receber o codigo em instantes.", codigoDeTeste));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<DadosMensagem> redefinirSenha(@RequestBody @Valid DadosRedefinirSenha dados) {
        recuperacaoSenhaService.redefinir(dados.email(), dados.codigo(), dados.novaSenha());
        return ResponseEntity.ok(new DadosMensagem("Senha alterada com sucesso. Entre com a nova senha."));
    }
}
