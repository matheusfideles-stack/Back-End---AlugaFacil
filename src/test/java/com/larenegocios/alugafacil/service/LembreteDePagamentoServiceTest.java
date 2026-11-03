package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.pagamento.LembretePagamento;
import com.larenegocios.alugafacil.domain.pagamento.LembretePagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LembreteDePagamentoServiceTest {

    private final Usuario locador = Dobles.usuario(1, Perfil.LOCADOR);
    private final Usuario locatario = Dobles.usuario(2, Perfil.LOCATARIO);

    private final ConfirmacaoEmailServiceTest.EmailFalso email = new ConfirmacaoEmailServiceTest.EmailFalso();
    private final List<LembretePagamento> registros = new ArrayList<>();
    private final Map<String, StatusPagamento> pagamentos = new HashMap<>();
    private Contrato contrato;
    private LembreteDePagamentoService service;

    @BeforeEach
    void montar() {
        email.enviados.clear();
        email.falha = false;
        registros.clear();
        pagamentos.clear();

        locador.setEmail("paulo@teste.com");
        locatario.setEmail("carla@teste.com");
        locatario.setNome("Carla");

        contrato = contratoDe(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), 10);

        service = new LembreteDePagamentoService();
        ReflectionTestUtils.setField(service, "contratoRepository", Dobles.repositorio(ContratoRepository.class)
                .responde("findByStatus", a -> contrato.getStatus() == a[0] ? List.of(contrato) : List.of())
                .criar());
        ReflectionTestUtils.setField(service, "pagamentoRepository", Dobles.repositorio(PagamentoRepository.class)
                .responde("findByContratoIdAndReferenciaMes", a -> {
                    StatusPagamento status = pagamentos.get((String) a[1]);
                    if (status == null) {
                        return Optional.empty();
                    }
                    Pagamento pagamento = new Pagamento();
                    pagamento.setStatus(status);
                    return Optional.of(pagamento);
                })
                .criar());
        ReflectionTestUtils.setField(service, "lembreteRepository", Dobles.repositorio(LembretePagamentoRepository.class)
                .responde("existsByContratoIdAndReferenciaMesAndEstagio", a -> registros.stream().anyMatch(r ->
                        r.getContrato().getId().equals(a[0]) && r.getReferenciaMes().equals(a[1]) && r.getEstagio() == a[2]))
                .responde("save", a -> {
                    registros.add((LembretePagamento) a[0]);
                    return a[0];
                })
                .criar());
        ReflectionTestUtils.setField(service, "envioDeEmail", email);
        ReflectionTestUtils.setField(service, "urlBase", "http://localhost:8080");
    }

    private Contrato contratoDe(LocalDate inicio, LocalDate fim, int diaVencimento) {
        Contrato novo = new Contrato();
        novo.setId(50L);
        novo.setImovel(Dobles.imovel(10, locador));
        novo.getImovel().setTitulo("Casa Azul");
        novo.setLocatario(locatario);
        novo.setDataInicio(inicio);
        novo.setDataFim(fim);
        novo.setCriadoEm(inicio.atStartOfDay());
        novo.setDiaVencimento(diaVencimento);
        novo.setValorMensal(new BigDecimal("1800.00"));
        novo.setStatus(StatusContrato.ATIVO);
        return novo;
    }

    private List<String[]> para(String endereco) {
        return email.enviados.stream().filter(e -> e[0].equals(endereco)).toList();
    }

    @Test
    void tresDiasAntesAvisaQueVaiVencer() {
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 7)));

        var e = para("carla@teste.com").get(0);
        assertTrue(e[1].contains("esta para vencer"));
        assertTrue(e[1].contains("Casa Azul"));
        assertTrue(e[2].contains("vence em 3 dia(s), em 10/03/2026"));
        assertTrue(e[2].contains("R$ 1.800,00"));
        assertTrue(e[2].contains("03/2026"));
        assertTrue(e[2].contains("http://localhost:8080/pagamentos.html"));
        assertTrue(para("paulo@teste.com").isEmpty());
    }

    @Test
    void antesDosTresDiasNaoAvisaNinguem() {
        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 6)));

        assertTrue(email.enviados.isEmpty());
    }

    @Test
    void noDiaDoVencimentoAvisaQueVenceHoje() {
        service.enviarLembretes(LocalDate.of(2026, 3, 10));

        var e = para("carla@teste.com").get(0);
        assertTrue(e[1].contains("vence hoje"));
        assertTrue(e[2].contains("vence hoje (10/03/2026)"));
    }

    @Test
    void umDiaDepoisAvisaOAtrasoSoAoLocatario() {
        service.enviarLembretes(LocalDate.of(2026, 3, 11));

        var e = para("carla@teste.com").get(0);
        assertTrue(e[1].contains("esta atrasado"));
        assertTrue(e[2].contains("atrasado ha 1 dia(s)"));
        assertTrue(para("paulo@teste.com").isEmpty());
    }

    @Test
    void tresDiasDeAtrasoAvisaTambemOLocador() {
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 13)));

        assertEquals(1, para("carla@teste.com").size());
        var paraOLocador = para("paulo@teste.com");
        assertEquals(1, paraOLocador.size());
        assertTrue(paraOLocador.get(0)[1].contains("aluguel atrasado: Casa Azul"));
        assertTrue(paraOLocador.get(0)[2].contains("Carla ainda nao pagou"));
        assertTrue(paraOLocador.get(0)[2].contains("3 dia(s) de atraso"));
        assertTrue(paraOLocador.get(0)[2].contains("/financeiro.html"));
    }

    @Test
    void seteDiasDeAtrasoAvisaSoOLocatario() {
        service.enviarLembretes(LocalDate.of(2026, 3, 17));

        assertEquals(1, para("carla@teste.com").size());
        assertTrue(para("paulo@teste.com").isEmpty());
    }

    @Test
    void mesJaPagoOuEmProcessamentoNaoRecebeLembrete() {
        pagamentos.put("2026-03", StatusPagamento.PAGO);
        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 11)));

        pagamentos.put("2026-03", StatusPagamento.PROCESSANDO);
        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 11)));

        assertTrue(email.enviados.isEmpty());
    }

    @Test
    void cobrancaPendenteOuRecusadaContinuaSendoLembrada() {
        pagamentos.put("2026-03", StatusPagamento.PENDENTE);
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 11)));

        pagamentos.put("2026-03", StatusPagamento.RECUSADO);
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 13)));
    }

    @Test
    void mesmoEstagioNaoSeRepeteNemNaMesmaRodadaNemNosDiasSeguintes() {
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 11)));
        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 11)));
        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 12)));

        assertEquals(1, email.enviados.size());
    }

    @Test
    void proximoEstagioGeraNovoAviso() {
        service.enviarLembretes(LocalDate.of(2026, 3, 10));
        service.enviarLembretes(LocalDate.of(2026, 3, 11));

        assertEquals(2, para("carla@teste.com").size());
    }

    @Test
    void sistemaForaDoArNoDiaExatoAvisaSoOEstagioMaisRecenteSemRepetirOsAnteriores() {
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 12)));

        var e = para("carla@teste.com");
        assertEquals(1, e.size());
        assertTrue(e.get(0)[2].contains("atrasado ha 2 dia(s)"));
    }

    @Test
    void falhaNoEmailNaoGravaRegistroEAProximaRodadaTentaDeNovo() {
        email.falha = true;
        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 11)));
        assertTrue(registros.isEmpty());

        email.falha = false;
        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 11)));
        assertEquals(1, registros.size());
    }

    @Test
    void contratoEncerradoNaoRecebeLembrete() {
        contrato.setStatus(StatusContrato.ENCERRADO);

        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 11)));
    }

    @Test
    void vencimentoAnteriorAoInicioDoContratoEIgnorado() {
        contrato = contratoDe(LocalDate.of(2026, 3, 20), LocalDate.of(2027, 3, 19), 10);

        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 25)));
    }

    @Test
    void vencimentoDepoisDoFimDoContratoEIgnorado() {
        contrato = contratoDe(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 10);
        pagamentos.put("2026-03", StatusPagamento.PAGO);

        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 4, 12)));
    }

    @Test
    void contratoCriadoDepoisDoVencimentoNaoCobraOMesQueJaPassou() {
        contrato.setCriadoEm(LocalDateTime.of(2026, 3, 20, 10, 0));

        assertEquals(0, service.enviarLembretes(LocalDate.of(2026, 3, 25)));
    }

    @Test
    void vencimentoNoInicioDoMesSeguinteEAvisadoAindaNoMesAnterior() {
        contrato = contratoDe(LocalDate.of(2026, 3, 15), LocalDate.of(2027, 3, 14), 2);

        assertEquals(1, service.enviarLembretes(LocalDate.of(2026, 3, 30)));

        assertTrue(para("carla@teste.com").get(0)[2].contains("vence em 3 dia(s), em 02/04/2026"));
        assertEquals("2026-04", registros.get(0).getReferenciaMes());
    }

    @Test
    void atrasoQueAtravessaAViradaDoMesContinuaSendoAvisado() {
        contrato = contratoDe(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), 28);

        // Venceu 28/03; em 04/04 ja sao 7 dias de atraso.
        service.enviarLembretes(LocalDate.of(2026, 4, 4));

        assertTrue(para("carla@teste.com").stream().anyMatch(e -> e[2].contains("atrasado ha 7 dia(s)")));
    }

    @Test
    void diaTrintaEUmEmMesCurtoVenceNoUltimoDia() {
        contrato = contratoDe(LocalDate.of(2026, 2, 1), LocalDate.of(2027, 1, 31), 31);

        service.enviarLembretes(LocalDate.of(2026, 2, 28));

        assertTrue(para("carla@teste.com").get(0)[2].contains("vence hoje (28/02/2026)"));
    }

    @Test
    void cadaMesTemSeusProprioslembretes() {
        service.enviarLembretes(LocalDate.of(2026, 3, 11));
        service.enviarLembretes(LocalDate.of(2026, 4, 11));

        List<String> meses = registros.stream().map(LembretePagamento::getReferenciaMes).toList();
        assertTrue(meses.contains("2026-03"));
        assertTrue(meses.contains("2026-04"));
    }
}
