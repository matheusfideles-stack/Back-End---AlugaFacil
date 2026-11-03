package com.larenegocios.alugafacil.infra.security;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Em producao o sistema recusa subir com segredo ou senha de administrador fracos, ou sem e-mail configurado
// (sem e-mail ninguem confirma a conta, porque o codigo nao aparece na tela): e melhor falhar na subida do que
// rodar assim sem ninguem perceber. Roda antes de AdministradorInicial.
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VerificacaoDeSegurancaNaSubida implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String segredoJwt;
    private final String senhaDoAdministrador;
    private final String servidorDeEmail;
    private final String provedorDePagamento;
    private final String tokenDoMercadoPago;
    private final String segredoDoWebhook;
    private final String urlDeNotificacao;

    public VerificacaoDeSegurancaNaSubida(UsuarioRepository usuarioRepository,
                                          PasswordEncoder passwordEncoder,
                                          @Value("${api.security.token.secret:}") String segredoJwt,
                                          @Value("${app.admin.senha:}") String senhaDoAdministrador,
                                          @Value("${spring.mail.host:}") String servidorDeEmail,
                                          @Value("${pagamento.gateway.provider:}") String provedorDePagamento,
                                          @Value("${pagamento.mercadopago.access-token:}") String tokenDoMercadoPago,
                                          @Value("${pagamento.mercadopago.webhook-secret:}") String segredoDoWebhook,
                                          @Value("${pagamento.mercadopago.notification-url:}") String urlDeNotificacao) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.segredoJwt = segredoJwt;
        this.senhaDoAdministrador = senhaDoAdministrador;
        this.servidorDeEmail = servidorDeEmail;
        this.provedorDePagamento = provedorDePagamento;
        this.tokenDoMercadoPago = tokenDoMercadoPago;
        this.segredoDoWebhook = segredoDoWebhook;
        this.urlDeNotificacao = urlDeNotificacao;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problemas = new ArrayList<>(RegrasDeSegurancaDeProducao.problemas(segredoJwt, senhaDoAdministrador));
        problemas.addAll(RegrasDeSegurancaDeProducao.problemasDePagamento(
                provedorDePagamento, tokenDoMercadoPago, segredoDoWebhook, urlDeNotificacao));

        if (servidorDeEmail == null || servidorDeEmail.isBlank()) {
            problemas.add("SPRING_MAIL_HOST nao foi definido. Sem e-mail ninguem consegue confirmar a conta "
                    + "(veja docs/EMAIL.md).");
        }

        usuarioRepository.findByPerfil(Perfil.ADMIN, Pageable.unpaged()).forEach(admin -> {
            boolean senhaConhecida = RegrasDeSegurancaDeProducao.SENHAS_CONHECIDAS.stream()
                    .anyMatch(conhecida -> passwordEncoder.matches(conhecida, admin.getSenha()));
            if (senhaConhecida) {
                problemas.add("O administrador " + admin.getEmail() + " ainda usa uma senha conhecida. "
                        + "Troque a senha antes de subir (veja docs/PRODUCAO.md).");
            }
        });

        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuracao insegura para producao:\n - " + String.join("\n - ", problemas));
        }
    }
}
