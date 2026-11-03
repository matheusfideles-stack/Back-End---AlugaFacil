package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// Imoveis cadastrados antes do mapa nao tem posicao. Ao subir, uma thread separada completa ate 100 deles, um por vez
// (o geocodificador ja respeita o intervalo do OpenStreetMap). Sem internet, desiste depois de poucas falhas seguidas.
@Component
@ConditionalOnProperty(name = "app.geocodificacao.ativa", havingValue = "true", matchIfMissing = true)
public class LocalizacaoDeImoveisAntigos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalizacaoDeImoveisAntigos.class);
    private static final int FALHAS_SEGUIDAS_PARA_DESISTIR = 5;

    @Autowired
    private ImovelRepository imovelRepository;

    @Autowired
    private ImovelService imovelService;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> pendentes = imovelRepository.findTop100ByLatitudeIsNullOrderByIdAsc().stream().map(Imovel::getId).toList();
        if (pendentes.isEmpty()) {
            return;
        }
        Thread tarefa = new Thread(() -> localizar(pendentes), "localizacao-de-imoveis");
        tarefa.setDaemon(true);
        tarefa.start();
    }

    private void localizar(List<Long> ids) {
        int falhasSeguidas = 0;
        int localizados = 0;
        for (Long id : ids) {
            try {
                imovelService.localizarPendente(id);
                boolean achou = imovelRepository.findById(id).map(i -> i.getLatitude() != null).orElse(false);
                falhasSeguidas = achou ? 0 : falhasSeguidas + 1;
                if (achou) {
                    localizados++;
                }
            } catch (RuntimeException e) {
                falhasSeguidas++;
                log.warn("Falha ao localizar o imovel {} no mapa", id, e);
            }
            if (falhasSeguidas >= FALHAS_SEGUIDAS_PARA_DESISTIR) {
                log.warn("Localizacao de imoveis antigos interrompida apos {} falhas seguidas; tenta de novo na proxima subida",
                        FALHAS_SEGUIDAS_PARA_DESISTIR);
                break;
            }
        }
        log.info("Imoveis antigos localizados no mapa: {} de {}", localizados, ids.size());
    }
}
