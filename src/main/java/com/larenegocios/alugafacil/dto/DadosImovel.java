package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.TipoImovel;

import java.math.BigDecimal;

public record DadosImovel(
        Long id,
        String titulo,
        String descricao,
        TipoImovel tipo,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        BigDecimal valorAluguel,
        BigDecimal valorCondominio,
        Boolean disponivel,
        Long locadorId,
        String nomeLocador
) {
    public DadosImovel(Imovel imovel) {
        this(imovel.getId(), imovel.getTitulo(), imovel.getDescricao(), imovel.getTipo(),
                imovel.getLogradouro(), imovel.getNumero(), imovel.getBairro(), imovel.getCidade(),
                imovel.getUf(), imovel.getCep(), imovel.getValorAluguel(), imovel.getValorCondominio(),
                imovel.getDisponivel(), imovel.getLocador().getId(), imovel.getLocador().getNome());
    }
}
