package com.larenegocios.alugafacil;

import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(PagamentoEmailComFalhaIntegrationTest.EmailQuePifa.class)
class PagamentoEmailComFalhaIntegrationTest extends IntegracaoBase {

    static class ServidorDeEmailFora extends EnvioDeEmail {

        final AtomicInteger tentativas = new AtomicInteger();

        ServidorDeEmailFora() {
            super(null, "teste@alugafacil.local");
        }

        @Override
        public boolean enviaDeVerdade() {
            return true;
        }

        @Override
        public void enviar(String para, String assunto, String texto) {
            tentativas.incrementAndGet();
            throw new IllegalStateException("servidor de e-mail fora do ar");
        }
    }

    @TestConfiguration
    static class EmailQuePifa {

        @Bean
        @Primary
        ServidorDeEmailFora servidorDeEmailFora() {
            return new ServidorDeEmailFora();
        }
    }

    @Autowired
    private ServidorDeEmailFora servidor;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    private String tokenLocatario;
    private long contratoId;

    @BeforeEach
    void criarContrato() throws Exception {
        String tokenLocador = tokenDe("LOCADOR");
        tokenLocatario = tokenDe("LOCATARIO");
        long imovelId = idDaResposta(mockMvc.perform(postJson("/imoveis", dadosImovel(), tokenLocador)));
        contratoId = contratar(imovelId, tokenLocatario, tokenLocador);
        servidor.tentativas.set(0);
    }

    @Test
    void falhaNoEmailNaoDesfazOPagamento() throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contratoId", contratoId);
        dados.put("referenciaMes", "2026-12");
        dados.put("metodo", "PIX");

        long pagamentoId = idDaResposta(mockMvc.perform(postJson("/pagamentos", dados, tokenLocatario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO")));

        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagamentoId).orElseThrow().getStatus());
        assertEquals(2, servidor.tentativas.get(), "tentou avisar os dois, mesmo com o primeiro falhando");
    }
}
