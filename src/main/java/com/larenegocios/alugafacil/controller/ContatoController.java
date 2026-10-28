package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.dto.DadosContato;
import com.larenegocios.alugafacil.dto.DadosMensagem;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contato")
public class ContatoController {

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Value("${app.email-suporte}")
    private String emailSuporte;

    @PostMapping
    public ResponseEntity<DadosMensagem> enviar(@RequestBody @Valid DadosContato dados) {
        String texto = "Nome: " + dados.nome() + "\n"
                + "Email para resposta: " + dados.email() + "\n\n"
                + dados.mensagem();
        envioDeEmail.enviar(emailSuporte, "[Contato AlugaFacil] " + dados.assunto(), texto);
        if (!envioDeEmail.enviaDeVerdade()) {
            return ResponseEntity.ok(new DadosMensagem(
                    "Mensagem registrada em modo de teste: este servidor ainda não tem e-mail configurado, "
                            + "então ela só aparece no log do servidor."));
        }
        return ResponseEntity.ok(new DadosMensagem("Mensagem enviada! Responderemos no email informado em até 24 horas."));
    }
}
