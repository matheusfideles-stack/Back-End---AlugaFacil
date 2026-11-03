package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosContatoProprietario;
import com.larenegocios.alugafacil.dto.DadosMensagemAoProprietario;
import com.larenegocios.alugafacil.service.ContatoDoImovelService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/imoveis/{imovelId}")
public class ContatoDoImovelController {

    @Autowired
    private ContatoDoImovelService contatoService;

    @GetMapping("/contato")
    public ResponseEntity<DadosContatoProprietario> contato(@PathVariable Long imovelId) {
        return ResponseEntity.ok(contatoService.contato(imovelId));
    }

    @PostMapping("/mensagens")
    public ResponseEntity<Void> enviarMensagem(@PathVariable Long imovelId,
                                               @RequestBody @Valid DadosMensagemAoProprietario dados,
                                               @AuthenticationPrincipal Usuario de) {
        contatoService.enviarMensagem(imovelId, dados.mensagem(), de);
        return ResponseEntity.noContent().build();
    }
}
