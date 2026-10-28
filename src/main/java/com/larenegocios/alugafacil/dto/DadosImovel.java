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
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        BigDecimal valorAluguel,
        BigDecimal valorCondominio,
        Integer quartos,
        Integer banheiros,
        Integer vagas,
        Integer areaM2,
        Boolean disponivel,
        Long locadorId,
        String nomeLocador,
        Double mediaAvaliacoes,
        Long totalAvaliacoes
) {
    public DadosImovel(Imovel imovel) {
        this(imovel, null, 0L);
    }

    public DadosImovel(Imovel imovel, Double mediaAvaliacoes, Long totalAvaliacoes) {
        this(imovel.getId(), imovel.getTitulo(), imovel.getDescricao(), imovel.getTipo(),
                imovel.getLogradouro(), imovel.getNumero(), imovel.getComplemento(), imovel.getBairro(),
                imovel.getCidade(), imovel.getUf(), imovel.getCep(), imovel.getValorAluguel(),
                imovel.getValorCondominio(), imovel.getQuartos(), imovel.getBanheiros(), imovel.getVagas(),
                imovel.getAreaM2(), imovel.getDisponivel(), imovel.getLocador().getId(),
                imovel.getLocador().getNome(), mediaAvaliacoes, totalAvaliacoes);
    }
}
