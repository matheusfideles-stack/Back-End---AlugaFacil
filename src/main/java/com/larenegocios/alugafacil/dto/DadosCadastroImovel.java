package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.imovel.Comodidade;
import com.larenegocios.alugafacil.domain.imovel.TipoImovel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

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

        BigDecimal valorCondominio,

        @Min(0)
        Integer quartos,

        @Min(0)
        Integer banheiros,

        @Min(0)
        Integer vagas,

        @Min(0)
        Integer areaM2,

        Set<Comodidade> comodidades) {
}
