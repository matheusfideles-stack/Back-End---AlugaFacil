package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.TokenRecuperacaoSenha;
import com.larenegocios.alugafacil.domain.usuario.TokenRecuperacaoSenhaRepository;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.security.GeradorDeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private static final String CODIGO_INVALIDO = "Codigo invalido ou expirado. Solicite um novo.";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Autowired
    private GeradorDeToken geradorDeToken;

    @Value("${app.url-base}")
    private String urlBase;

    @Value("${app.recuperacao-senha.validade-minutos:30}")
    private long validadeMinutos;

    @Value("${app.codigo.max-tentativas:5}")
    private int maxTentativas;

    @Value("${app.exibir-links-de-teste:false}")
    private boolean exibirLinksDeTeste;

    // So devolve o codigo quando exibir-links-de-teste esta ligado (perfil dev) e nao ha SMTP: nunca em producao.
    // Fora disso a resposta ao cliente e sempre a mesma, exista ou nao o e-mail, para nao revelar quem tem conta.
    @Transactional
    public Optional<String> solicitar(String emailInformado) {
        String email = AutenticacaoService.normalizarEmail(emailInformado);

        return usuarioRepository.findUsuarioByEmail(email).filter(Usuario::isEnabled).flatMap(usuario -> {
            LocalDateTime agora = LocalDateTime.now();
            tokenRepository.invalidarPendentes(usuario.getId(), agora);

            String codigo;
            String hash;
            do {
                codigo = geradorDeToken.gerarCodigo();
                hash = geradorDeToken.hash(usuario.getId(), codigo);
            } while (tokenRepository.existsByTokenHash(hash));

            TokenRecuperacaoSenha registro = new TokenRecuperacaoSenha();
            registro.setUsuario(usuario);
            registro.setTokenHash(hash);
            registro.setExpiraEm(agora.plusMinutes(validadeMinutos));
            tokenRepository.save(registro);

            enviarCodigo(usuario, codigo);
            return exibirLinksDeTeste && !envioDeEmail.enviaDeVerdade() ? Optional.of(codigo) : Optional.empty();
        });
    }

    // Digitar o codigo enviado ao e-mail prova que a pessoa le aquela caixa, entao a conta tambem fica confirmada.
    // Errar o codigo gasta uma tentativa; por isso o erro nao pode desfazer a transacao.
    @Transactional(noRollbackFor = RegraDeNegocioException.class)
    public void redefinir(String emailInformado, String codigo, String novaSenha) {
        LocalDateTime agora = LocalDateTime.now();
        Usuario usuario = usuarioRepository.findUsuarioByEmail(AutenticacaoService.normalizarEmail(emailInformado))
                .orElseThrow(() -> new RegraDeNegocioException(CODIGO_INVALIDO));
        TokenRecuperacaoSenha registro = tokenRepository
                .findFirstByUsuarioIdAndUsadoEmIsNullOrderByIdDesc(usuario.getId())
                .filter(t -> t.utilizavel(agora) && t.getTentativas() < maxTentativas)
                .orElseThrow(() -> new RegraDeNegocioException(CODIGO_INVALIDO));

        if (!geradorDeToken.confere(registro.getTokenHash(), usuario.getId(), codigo)) {
            registro.setTentativas(registro.getTentativas() + 1);
            tokenRepository.save(registro);
            throw new RegraDeNegocioException(CODIGO_INVALIDO);
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setEmailConfirmado(true);
        usuarioRepository.save(usuario);
        tokenRepository.invalidarPendentes(usuario.getId(), agora);
    }

    private void enviarCodigo(Usuario usuario, String codigo) {
        String texto = "Ola, " + usuario.getNome() + "!\n\n"
                + "Voce informou que esqueceu a senha da sua conta no AlugaFacil.\n"
                + "Seu codigo para criar uma nova senha e:\n\n"
                + "        " + codigo + "\n\n"
                + "Digite esse codigo em " + urlBase + "/redefinir-senha.html. "
                + "Ele vale por " + validadeMinutos + " minutos e so pode ser usado uma vez.\n\n"
                + "Se voce nao fez esse pedido, ignore este e-mail: sua senha continua a mesma.";
        try {
            envioDeEmail.enviar(usuario.getEmail(), "AlugaFacil - voce esqueceu a senha? Use seu codigo para redefinir", texto);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de recuperacao para o usuario {}", usuario.getId(), e);
        }
    }
}
