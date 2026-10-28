package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DadosCadastroAvaliacao(

        @NotNull
        @Min(value = 0, message = "a nota minima e 0")
        @Max(value = 5, message = "a nota maxima e 5")
        Integer nota,

        @Size(max = 1000, message = "o comentario pode ter no maximo 1000 caracteres")
        String comentario) {
}
