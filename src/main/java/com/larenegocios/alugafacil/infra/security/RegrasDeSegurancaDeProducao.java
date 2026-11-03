package com.larenegocios.alugafacil.infra.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RegrasDeSegurancaDeProducao {

    public static final String SEGREDO_DE_EXEMPLO = "alugafacil-secret-trocar-em-producao";
    public static final int TAMANHO_MINIMO_DO_SEGREDO = 32;
    public static final int TAMANHO_MINIMO_DA_SENHA = 10;

    // Senhas que aparecem no README, nos testes ou em qualquer lista de "senhas comuns". Servem para achar um
    // administrador que ja exista no banco com uma delas (as novas ja caem na regra do tamanho minimo).
    public static final List<String> SENHAS_CONHECIDAS = List.of(
            "Admin1234", "Senha123!", "Senha1234", "12345678", "123456789", "password", "senha123", "admin123");

    private RegrasDeSegurancaDeProducao() {
    }

    public static List<String> problemas(String segredoJwt, String senhaDoAdministrador) {
        List<String> problemas = new ArrayList<>();

        if (segredoJwt == null || segredoJwt.isBlank()) {
            problemas.add("JWT_SECRET nao foi definido.");
        } else {
            String minusculas = segredoJwt.toLowerCase(Locale.ROOT);
            if (segredoJwt.equals(SEGREDO_DE_EXEMPLO) || minusculas.contains("trocar") || minusculas.contains("troque")) {
                problemas.add("JWT_SECRET ainda e o valor de exemplo. Gere um segredo proprio.");
            } else if (segredoJwt.length() < TAMANHO_MINIMO_DO_SEGREDO) {
                problemas.add("JWT_SECRET precisa ter pelo menos " + TAMANHO_MINIMO_DO_SEGREDO + " caracteres.");
            }
        }

        if (senhaDoAdministrador != null && !senhaDoAdministrador.isBlank()
                && senhaDoAdministrador.length() < TAMANHO_MINIMO_DA_SENHA) {
            problemas.add("ADMIN_SENHA precisa ter pelo menos " + TAMANHO_MINIMO_DA_SENHA + " caracteres.");
        }

        return problemas;
    }

    // Em producao o gateway "fake" aprovaria qualquer pagamento sem cobrar ninguem, e sem o segredo do webhook
    // qualquer pessoa poderia mandar avisos falsos de pagamento para o sistema.
    public static List<String> problemasDePagamento(String provider, String accessToken, String segredoDoWebhook,
                                                    String urlDeNotificacao) {
        List<String> problemas = new ArrayList<>();

        if (provider == null || !provider.trim().equalsIgnoreCase("mercadopago")) {
            problemas.add("PAGAMENTO_PROVIDER precisa ser mercadopago. Qualquer outro valor (como fake) aprova "
                    + "pagamentos sem cobrar ninguem.");
            return problemas;
        }
        if (accessToken == null || accessToken.isBlank()) {
            problemas.add("MP_ACCESS_TOKEN nao foi definido.");
        }
        if (segredoDoWebhook == null || segredoDoWebhook.isBlank()) {
            problemas.add("MP_WEBHOOK_SECRET nao foi definido. Sem ele qualquer pessoa pode enviar avisos falsos "
                    + "de pagamento.");
        }
        if (urlDeNotificacao == null || !urlDeNotificacao.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            problemas.add("MP_NOTIFICATION_URL precisa ser o endereco https publico do webhook "
                    + "(https://seu-dominio/webhooks/mercadopago). Sem ele Pix e boleto nunca ficam como pagos.");
        }
        return problemas;
    }
}
