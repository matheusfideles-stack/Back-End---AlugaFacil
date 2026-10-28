package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.TokenConfirmacaoEmail;
import com.larenegocios.alugafacil.domain.usuario.TokenConfirmacaoEmailRepository;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.security.GeradorDeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ConfirmacaoEmailService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoEmailService.class);

    private static final String CODIGO_INVALIDO = "Codigo invalido ou expirado. Peca um novo codigo.";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenConfirmacaoEmailRepository tokenRepository;

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Autowired
    private GeradorDeToken geradorDeToken;

    @Value("${app.url-base}")
    private String urlBase;

    @Value("${app.confirmacao-email.validade-minutos:60}")
    private long validadeMinutos;

    @Value("${app.codigo.max-tentativas:5}")
    private int maxTentativas;

    @Value("${app.exibir-links-de-teste:false}")
    private boolean exibirLinksDeTeste;

    // O codigo so volta na resposta quando exibir-links-de-teste esta ligado (perfil dev) e nao ha SMTP.
    @Transactional
    public Optional<String> enviarConfirmacao(Usuario usuario) {
        LocalDateTime agora = LocalDateTime.now();
        tokenRepository.invalidarPendentes(usuario.getId(), agora);

        String codigo;
        String hash;
        do {
            codigo = geradorDeToken.gerarCodigo();
            hash = geradorDeToken.hash(usuario.getId(), codigo);
        } while (tokenRepository.existsByTokenHash(hash));

        TokenConfirmacaoEmail registro = new TokenConfirmacaoEmail();
        registro.setUsuario(usuario);
        registro.setTokenHash(hash);
        registro.setExpiraEm(agora.plusMinutes(validadeMinutos));
        tokenRepository.save(registro);

        String texto = "Ola, " + usuario.getNome() + "!\n\n"
                + "Bem-vindo(a) ao AlugaFacil. Seu codigo de verificacao e:\n\n"
                + "        " + codigo + "\n\n"
                + "Digite esse codigo em " + urlBase + "/confirmar-email.html (ou na tela de login) para ativar sua conta. "
                + "Ele vale por " + validadeMinutos + " minutos e so pode ser usado uma vez.\n\n"
                + "Sem essa confirmacao nao e possivel entrar na plataforma.\n"
                + "Se voce nao criou esta conta, ignore este e-mail.";
        try {
            envioDeEmail.enviar(usuario.getEmail(), "AlugaFacil - seu codigo de verificacao", texto);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de confirmacao para o usuario {}", usuario.getId(), e);
        }
        return exibirLinksDeTeste && !envioDeEmail.enviaDeVerdade() ? Optional.of(codigo) : Optional.empty();
    }

    // Errar o codigo gasta uma tentativa; por isso o erro nao pode desfazer a transacao.
    @Transactional(noRollbackFor = RegraDeNegocioException.class)
    public void confirmar(String emailInformado, String codigo) {
        LocalDateTime agora = LocalDateTime.now();
        Usuario usuario = usuarioRepository.findUsuarioByEmail(AutenticacaoService.normalizarEmail(emailInformado))
                .orElseThrow(() -> new RegraDeNegocioException(CODIGO_INVALIDO));
        TokenConfirmacaoEmail registro = tokenRepository
                .findFirstByUsuarioIdAndUsadoEmIsNullOrderByIdDesc(usuario.getId())
                .filter(t -> t.utilizavel(agora) && t.getTentativas() < maxTentativas)
                .orElseThrow(() -> new RegraDeNegocioException(CODIGO_INVALIDO));

        if (!geradorDeToken.confere(registro.getTokenHash(), usuario.getId(), codigo)) {
            registro.setTentativas(registro.getTentativas() + 1);
            tokenRepository.save(registro);
            throw new RegraDeNegocioException(CODIGO_INVALIDO);
        }

        usuario.setEmailConfirmado(true);
        usuarioRepository.save(usuario);
        tokenRepository.invalidarPendentes(usuario.getId(), agora);
    }

    // Resposta igual exista ou nao a conta, para nao revelar quem tem cadastro.
    @Transactional
    public Optional<String> reenviar(String emailInformado) {
        String email = AutenticacaoService.normalizarEmail(emailInformado);
        return usuarioRepository.findUsuarioByEmail(email)
                .filter(Usuario::isEnabled)
                .filter(usuario -> !usuario.getEmailConfirmado())
                .flatMap(this::enviarConfirmacao);
    }
}
