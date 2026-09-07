package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.imovel.TipoImovel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DadosCadastroImovel(

        @NotBlank
        String titulo,

        String descricao,

        @NotNull
        TipoImovel tipo,

        @NotBlank
        String logradouro,

        @NotBlank
        String numero,

        String complemento,

        @NotBlank
        String bairro,

        @NotBlank
        String cidade,

        @NotBlank
        String uf,

        @NotBlank
        String cep,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal valorAluguel,

        BigDecimal valorCondominio) {
}
