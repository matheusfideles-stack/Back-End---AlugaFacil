package com.larenegocios.alugafacil.domain.pagamento;

import java.time.LocalDate;
import java.util.Optional;

// Cada estagio e avisado uma unica vez por contrato e mes. Em cada rodada vale so o estagio mais avancado que ja
// chegou: se o sistema ficou fora do ar no dia exato, o aviso sai no dia seguinte sem repetir os anteriores.
public enum EstagioDeLembrete {

    ANTECEDENCIA(-3),
    VENCE_HOJE(0),
    ATRASO_1(1),
    ATRASO_3(3),
    ATRASO_7(7);

    private final int diasEmRelacaoAoVencimento;

    EstagioDeLembrete(int diasEmRelacaoAoVencimento) {
        this.diasEmRelacaoAoVencimento = diasEmRelacaoAoVencimento;
    }

    public int diasEmRelacaoAoVencimento() {
        return diasEmRelacaoAoVencimento;
    }

    public LocalDate dataPara(LocalDate vencimento) {
        return vencimento.plusDays(diasEmRelacaoAoVencimento);
    }

    public boolean ehAtraso() {
        return diasEmRelacaoAoVencimento > 0;
    }

    public static Optional<EstagioDeLembrete> maisRecente(LocalDate hoje, LocalDate vencimento) {
        EstagioDeLembrete escolhido = null;
        for (EstagioDeLembrete estagio : values()) {
            if (!estagio.dataPara(vencimento).isAfter(hoje)) {
                escolhido = estagio;
            }
        }
        return Optional.ofNullable(escolhido);
    }
}
