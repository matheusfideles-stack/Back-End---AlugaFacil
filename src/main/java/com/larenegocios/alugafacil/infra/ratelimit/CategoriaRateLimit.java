package com.larenegocios.alugafacil.infra.ratelimit;

public enum CategoriaRateLimit {
    AUTENTICACAO,
    PAGAMENTOS,
    WEBHOOKS,
    GERAL;

    public static CategoriaRateLimit de(String caminho) {
        if (caminho.equals("/auth") || caminho.startsWith("/auth/")) {
            return AUTENTICACAO;
        }
        if (caminho.equals("/pagamentos") || caminho.startsWith("/pagamentos/")) {
            return PAGAMENTOS;
        }
        if (caminho.equals("/webhooks") || caminho.startsWith("/webhooks/")) {
            return WEBHOOKS;
        }
        return GERAL;
    }
}
