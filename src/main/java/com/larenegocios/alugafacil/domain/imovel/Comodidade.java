package com.larenegocios.alugafacil.domain.imovel;

// O que o imovel oferece. O nome do valor e o que trafega na API; o rotulo aparece na tela.
public enum Comodidade {
    PISCINA("Piscina"),
    ESTACIONAMENTO_GRATUITO("Estacionamento gratuito"),
    MAQUINA_DE_LAVAR("Máquina de lavar"),
    AR_CONDICIONADO("Ar-condicionado"),
    LAREIRA("Lareira"),
    TV("TV"),
    AQUECEDOR("Aquecedor"),
    CHURRASQUEIRA("Churrasqueira");

    private final String rotulo;

    Comodidade(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
