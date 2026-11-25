package com.larenegocios.alugafacil;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Servidor sem codigo por e-mail: a conta nasce confirmada, e o cadastro exige CPF valido e e-mail de provedor conhecido.
@TestPropertySource(properties = "EXIGIR_CONFIRMACAO_EMAIL=false")
class CadastroSemConfirmacaoIntegrationTest extends IntegracaoBase {

    private static final AtomicLong SEQUENCIA = new AtomicLong(100_000_000L);

    private static String cpfValido() {
        String base = String.format("%09d", SEQUENCIA.incrementAndGet());
        int d1 = digito(base);
        int d2 = digito(base + d1);
        return base + d1 + d2;
    }

    private static int digito(String digitos) {
        int soma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            soma += (digitos.charAt(i) - '0') * (digitos.length() + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private Map<String, Object> dados(String email, String cpf) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", "Maria Teste");
        dados.put("email", email);
        dados.put("cpf", cpf);
        dados.put("telefone", "34999999999");
        dados.put("senha", SENHA);
        return dados;
    }

    @Test
    void contaNasceConfirmadaEEntraSemCodigo() throws Exception {
        Map<String, Object> dados = dados("maria" + SEQUENCIA.incrementAndGet() + "@gmail.com", cpfValido());

        mockMvc.perform(postJson("/auth/registro", dados))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmado").value(true));

        mockMvc.perform(postJson("/auth/login", Map.of("email", dados.get("email"), "senha", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void cpfComDigitosInvalidosEhRecusado() throws Exception {
        mockMvc.perform(postJson("/auth/registro", dados("joao" + SEQUENCIA.incrementAndGet() + "@gmail.com", "12345678900")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cpfComTodosOsDigitosIguaisEhRecusado() throws Exception {
        mockMvc.perform(postJson("/auth/registro", dados("ana" + SEQUENCIA.incrementAndGet() + "@gmail.com", "11111111111")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailDeProvedorDesconhecidoEhRecusado() throws Exception {
        mockMvc.perform(postJson("/auth/registro", dados("fake" + SEQUENCIA.incrementAndGet() + "@demo.com", cpfValido())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailInstitucionalEduBrEhAceito() throws Exception {
        mockMvc.perform(postJson("/auth/registro", dados("aluno" + SEQUENCIA.incrementAndGet() + "@unipam.edu.br", cpfValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmado").value(true));
    }

    @Test
    void naoGeraCodigoDeConfirmacaoNemTokenPendente() throws Exception {
        Map<String, Object> dados = dados("carla" + SEQUENCIA.incrementAndGet() + "@outlook.com", cpfValido());

        mockMvc.perform(postJson("/auth/registro", dados))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoDeTeste").doesNotExist());
    }
}
