package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.manutencao.StatusManutencao;
import jakarta.validation.constraints.NotNull;

public record DadosAtualizacaoManutencao(

        @NotNull
        StatusManutencao status) {
}
