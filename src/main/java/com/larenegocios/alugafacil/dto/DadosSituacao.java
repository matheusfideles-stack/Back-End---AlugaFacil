package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.NotNull;

public record DadosSituacao(
        @NotNull
        Boolean ativo) {
}
