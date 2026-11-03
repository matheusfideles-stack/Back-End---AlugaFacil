package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DadosCadastroProposta(

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
        Integer diaVencimento,

        @Size(max = 500, message = "a mensagem pode ter no maximo 500 caracteres")
        String mensagem) {
}
