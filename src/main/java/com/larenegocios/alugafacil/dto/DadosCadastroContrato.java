package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DadosCadastroContrato(

        @NotNull
        Long imovelId,

        @NotNull
        LocalDate dataInicio,

        @NotNull
        @Future
        LocalDate dataFim,

        @NotNull
        @Min(1)
        @Max(28)
        Integer diaVencimento) {
}
