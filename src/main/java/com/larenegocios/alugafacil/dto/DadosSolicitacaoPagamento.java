package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DadosSolicitacaoPagamento(

        @NotNull
        Long contratoId,

        @NotNull
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "use o formato AAAA-MM")
        String referenciaMes,

        @NotNull
        MetodoPagamento metodo,

        String tokenCartao,

        String bandeiraCartao,

        @Min(value = 1, message = "o minimo e 1 parcela")
        @Max(value = 12, message = "o maximo e 12 parcelas")
        Integer parcelas) {
}
