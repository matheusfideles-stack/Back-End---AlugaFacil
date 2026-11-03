package com.larenegocios.alugafacil.infra.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegrasDeSegurancaDeProducaoTest {

    private static final String SEGREDO_BOM = "x7Kp2vQmL9sRtW4zYb8NcD1fGh5JaE3u";

    @Test
    void segredoEForteESenhaVaziaNaoGeramProblemas() {
        assertTrue(RegrasDeSegurancaDeProducao.problemas(SEGREDO_BOM, "").isEmpty());
        assertTrue(RegrasDeSegurancaDeProducao.problemas(SEGREDO_BOM, null).isEmpty());
    }

    @Test
    void segredoAusenteEhProblema() {
        assertEquals(1, RegrasDeSegurancaDeProducao.problemas(null, "").size());
        assertEquals(1, RegrasDeSegurancaDeProducao.problemas("   ", "").size());
    }

    @Test
    void segredoDeExemploEhProblema() {
        List<String> problemas = RegrasDeSegurancaDeProducao.problemas(RegrasDeSegurancaDeProducao.SEGREDO_DE_EXEMPLO, "");

        assertEquals(1, problemas.size());
        assertTrue(problemas.get(0).contains("exemplo"));
    }

    @Test
    void segredoComPalavraTroqueOuTrocarEhProblemaMesmoSendoLongo() {
        assertEquals(1, RegrasDeSegurancaDeProducao.problemas("troque-esta-chave-em-producao-por-favor", "").size());
        assertEquals(1, RegrasDeSegurancaDeProducao.problemas("PRECISA-TROCAR-ESTA-CHAVE-ANTES-DE-USAR", "").size());
    }

    @Test
    void segredoCurtoEhProblema() {
        List<String> problemas = RegrasDeSegurancaDeProducao.problemas("a".repeat(31), "");

        assertEquals(1, problemas.size());
        assertTrue(problemas.get(0).contains("32"));
        assertTrue(RegrasDeSegurancaDeProducao.problemas("a".repeat(32), "").isEmpty());
    }

    @Test
    void senhaDoAdministradorCurtaEhProblema() {
        assertEquals(1, RegrasDeSegurancaDeProducao.problemas(SEGREDO_BOM, "Admin1234").size());
        assertTrue(RegrasDeSegurancaDeProducao.problemas(SEGREDO_BOM, "UmaSenhaLonga9!").isEmpty());
    }

    @Test
    void problemasSeAcumulam() {
        assertEquals(2, RegrasDeSegurancaDeProducao.problemas("curto", "123").size());
    }

    @Test
    void mensagensNuncaRepetemOSegredoNemASenha() {
        String texto = String.join(" ", RegrasDeSegurancaDeProducao.problemas("segredoCurtoSecreto", "senhaCurta"));

        assertTrue(!texto.contains("segredoCurtoSecreto"));
        assertTrue(!texto.contains("senhaCurta"));
    }
}
