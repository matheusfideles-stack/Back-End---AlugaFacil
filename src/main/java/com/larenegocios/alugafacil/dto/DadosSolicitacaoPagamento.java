package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DadosSolicitacaoPagamento(

        @NotNull
        Long contratoId,

        @NotNull
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "use o formato AAAA-MM")
        String referenciaMes,

        @NotNull
        MetodoPagamento metodo) {
}
