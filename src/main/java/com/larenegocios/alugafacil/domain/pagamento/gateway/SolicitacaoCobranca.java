package com.larenegocios.alugafacil.domain.pagamento.gateway;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados enviados ao gateway de pagamento para gerar uma cobranca.
 *
 * @param pagamentoId    id do pagamento local, usado como external_reference e chave de idempotencia
 * @param valor          valor da cobranca
 * @param metodo         meio de pagamento escolhido
 * @param descricao      descricao exibida ao pagador
 * @param pagador        dados do locatario
 * @param vencimento     data de vencimento (usada para expiracao do boleto/Pix)
 * @param tokenCartao    token do cartao gerado no frontend (obrigatorio para cartao)
 * @param parcelas       numero de parcelas (cartao de credito; debito aceita apenas 1)
 * @param bandeiraCartao payment_method_id do cartao no provedor (ex.: "master", "visa", "debelo")
 */
public record SolicitacaoCobranca(
        Long pagamentoId,
        BigDecimal valor,
        MetodoPagamento metodo,
        String descricao,
        Pagador pagador,
        LocalDate vencimento,
        String tokenCartao,
        Integer parcelas,
        String bandeiraCartao
) {
}
