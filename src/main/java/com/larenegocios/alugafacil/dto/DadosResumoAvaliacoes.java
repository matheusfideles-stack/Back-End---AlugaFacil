package com.larenegocios.alugafacil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record DadosResumoAvaliacoes(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double media,
        long total,
        long[] distribuicao
) {
}
