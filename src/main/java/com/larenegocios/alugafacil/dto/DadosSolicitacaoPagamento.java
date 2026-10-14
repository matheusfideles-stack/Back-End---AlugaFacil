package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitacao de cobranca.
 * <p>
 * Para PIX e BOLETO basta contrato, mes de referencia e metodo.
 * Para CARTAO_CREDITO e CARTAO_DEBITO o frontend precisa tokenizar o cartao com a
 * public key do Mercado Pago e enviar {@code tokenCartao} e {@code bandeiraCartao}:
 * numero, CVV e validade nunca devem chegar ao backend.
 */
public record DadosSolicitacaoPagamento(

        @NotNull
        Long contratoId,

        @NotNull
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "use o formato AAAA-MM")
        String referenciaMes,

        @NotNull
        MetodoPagamento metodo,

        /** Token do cartao gerado no frontend. Obrigatorio para cartao de credito e debito. */
        String tokenCartao,

        /** payment_method_id do cartao (ex.: "master", "visa", "elo", "debmaster", "debvisa", "debelo"). */
        String bandeiraCartao,

        /** Numero de parcelas do cartao de credito. Debito aceita apenas 1. */
        @Min(value = 1, message = "o minimo e 1 parcela")
        @Max(value = 12, message = "o maximo e 12 parcelas")
        Integer parcelas) {
}
