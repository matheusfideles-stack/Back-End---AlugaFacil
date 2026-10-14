package com.larenegocios.alugafacil.infra.ratelimit;

public enum CategoriaRateLimit {
    /** Login e cadastro: protege contra tentativa de senha em massa. */
    AUTENTICACAO,
    /** Qualquer rota de pagamento: gerar Pix/boleto/cartao, sincronizar, estornar. */
    PAGAMENTOS,
    /** Notificacoes do gateway de pagamento. */
    WEBHOOKS,
    /** Todo o resto da API. */
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
