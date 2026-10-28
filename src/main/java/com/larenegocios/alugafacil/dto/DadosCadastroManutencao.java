package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.manutencao.UrgenciaManutencao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DadosCadastroManutencao(

        @NotNull
        Long contratoId,

        @NotBlank
        @Size(max = 50)
        String tipo,

        @NotNull
        UrgenciaManutencao urgencia,

        @NotBlank
        @Size(min = 10, max = 1000, message = "descreva o problema com 10 a 1000 caracteres")
        String descricao) {
}
