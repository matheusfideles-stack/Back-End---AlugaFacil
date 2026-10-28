package com.larenegocios.alugafacil.infra.security;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.service.AutenticacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);
    private static final String CPF_ADMINISTRADOR = "00000000000";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.senha:}")
    private String senha;

    @Value("${app.admin.nome:Administrador}")
    private String nome;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || senha.isBlank()) {
            return;
        }

        String emailNormalizado = AutenticacaoService.normalizarEmail(email);
        if (usuarioRepository.existsByEmailOrCpf(emailNormalizado, CPF_ADMINISTRADOR)) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setNome(nome);
        admin.setEmail(emailNormalizado);
        admin.setCpf(CPF_ADMINISTRADOR);
        admin.setSenha(passwordEncoder.encode(senha));
        admin.setPerfil(Perfil.ADMIN);
        admin.setAtivo(true);
        usuarioRepository.save(admin);
        log.info("Administrador inicial criado: {}", emailNormalizado);
    }
}
