package com.larenegocios.alugafacil.dto;

import com.larenegocios.alugafacil.domain.imovel.FotoImovel;

public record DadosFoto(Long id, String url, boolean capa) {

    public DadosFoto(FotoImovel foto, boolean capa) {
        this(foto.getId(), "/imoveis/" + foto.getImovel().getId() + "/fotos/" + foto.getId() + "/arquivo", capa);
    }
}
