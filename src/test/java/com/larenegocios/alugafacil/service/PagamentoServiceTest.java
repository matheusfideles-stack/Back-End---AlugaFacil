package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoConfirmadoEvento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGateway;
import com.larenegocios.alugafacil.domain.pagamento.gateway.RespostaCobranca;
import com.larenegocios.alugafacil.domain.pagamento.gateway.SolicitacaoCobranca;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosSolicitacaoPagamento;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagamentoServiceTest {

    private final Usuario locador = Dobles.usuario(1, Perfil.LOCADOR);
    private final Usuario locatario = Dobles.usuario(2, Perfil.LOCATARIO);
    private final Usuario estranho = Dobles.usuario(3, Perfil.LOCATARIO);

    private final Map<Long, Pagamento> banco = new HashMap<>();
    private final List<SolicitacaoCobranca> cobrancas = new ArrayList<>();
    private final List<Object> eventos = new ArrayList<>();
    private final List<String> estornos = new ArrayList<>();
    private RespostaCobranca respostaDaCriacao;
    private RespostaCobranca respostaDaConsulta;
    private Contrato contrato;
    private PagamentoService service;

    private static RespostaCobranca resposta(String id, StatusPagamento status) {
        return new RespostaCobranca(id, status, status.name().toLowerCase(), null, null, null, null, null, null);
    }

    @BeforeEach
    void montar() {
        banco.clear();
        cobrancas.clear();
        eventos.clear();
        estornos.clear();
        respostaDaCriacao = resposta("TX-1", StatusPagamento.PAGO);
        respostaDaConsulta = resposta("TX-1", StatusPagamento.PAGO);

        locatario.setCpf("12345678900");
        contrato = new Contrato();
        contrato.setId(50L);
        contrato.setImovel(Dobles.imovel(10, locador));
        contrato.setLocatario(locatario);
        contrato.setStatus(StatusContrato.ATIVO);
        contrato.setDiaVencimento(10);
        contrato.setValorMensal(new BigDecimal("1800.00"));

        ContratoService contratoService = new ContratoService();
        ReflectionTestUtils.setField(contratoService, "contratoRepository", Dobles.repositorio(ContratoRepository.class)
                .responde("findById", a -> a[0].equals(50L) ? Optional.of(contrato) : Optional.empty())
                .criar());

        PagamentoRepository repositorio = Dobles.repositorio(PagamentoRepository.class)
                .responde("findByContratoIdAndReferenciaMes", a -> banco.values().stream()
                        .filter(p -> p.getContrato().getId().equals(a[0]) && p.getReferenciaMes().equals(a[1]))
                        .findFirst())
                .responde("findByIdTransacaoGateway", a -> banco.values().stream()
                        .filter(p -> a[0].equals(p.getIdTransacaoGateway())).findFirst())
                .responde("findById", a -> Optional.ofNullable(banco.get((Long) a[0])))
                .responde("saveAndFlush", a -> {
                    Pagamento pagamento = (Pagamento) a[0];
                    if (pagamento.getId() == null) {
                        pagamento.setId(banco.size() + 1L);
                    }
                    banco.put(pagamento.getId(), pagamento);
                    return pagamento;
                })
                .criar();

        PaymentGateway gateway = new PaymentGateway() {
            @Override
            public RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao) {
                cobrancas.add(solicitacao);
                return respostaDaCriacao;
            }

            @Override
            public RespostaCobranca consultarCobranca(String idTransacaoGateway) {
                return respostaDaConsulta;
            }

            @Override
            public void estornarCobranca(String idTransacaoGateway) {
                estornos.add(idTransacaoGateway);
            }
        };

        service = new PagamentoService();
        ReflectionTestUtils.setField(service, "pagamentoRepository", repositorio);
        ReflectionTestUtils.setField(service, "contratoService", contratoService);
        ReflectionTestUtils.setField(service, "paymentGateway", gateway);
        ReflectionTestUtils.setField(service, "eventos", (org.springframework.context.ApplicationEventPublisher) eventos::add);
    }

    private DadosSolicitacaoPagamento pix(String mes) {
        return new DadosSolicitacaoPagamento(50L, mes, MetodoPagamento.PIX, null, null, null);
    }

    private DadosSolicitacaoPagamento cartao(MetodoPagamento metodo, String token, String bandeira, Integer parcelas) {
        return new DadosSolicitacaoPagamento(50L, "2026-12", metodo, token, bandeira, parcelas);
    }

    @Test
    void cobrancaUsaOValorMensalDoContratoEOVencimentoDoMes() {
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertEquals(new BigDecimal("1800.00"), pagamento.getValor());
        assertEquals(LocalDate.of(2026, 12, 10), pagamento.getVencimento());
        assertEquals("TX-1", pagamento.getIdTransacaoGateway());
    }

    @Test
    void vencimentoNoDia28CaiNoUltimoDiaDeFevereiro() {
        contrato.setDiaVencimento(28);

        assertEquals(LocalDate.of(2027, 2, 28), service.gerarCobranca(pix("2027-02"), locatario).getVencimento());
    }

    @Test
    void vencimentoQueNaoExisteNoMesVaiParaOUltimoDia() {
        contrato.setDiaVencimento(31);

        assertEquals(LocalDate.of(2027, 2, 28), service.gerarCobranca(pix("2027-02"), locatario).getVencimento());
        assertEquals(LocalDate.of(2028, 2, 29), service.gerarCobranca(pix("2028-02"), locatario).getVencimento());
    }

    @Test
    void gatewayRecebeOPagadorComNomeSeparadoDoSobrenome() {
        locatario.setNome("Maria da Silva Souza");

        service.gerarCobranca(pix("2026-12"), locatario);

        var pagador = cobrancas.get(0).pagador();
        assertEquals("Maria", pagador.nome());
        assertEquals("da Silva Souza", pagador.sobrenome());
        assertEquals("12345678900", pagador.cpf());
        assertEquals(locatario.getEmail(), pagador.email());
    }

    @Test
    void nomeComUmaPalavraSoRepeteComoSobrenome() {
        locatario.setNome("Madonna");

        service.gerarCobranca(pix("2026-12"), locatario);

        assertEquals("Madonna", cobrancas.get(0).pagador().nome());
        assertEquals("Madonna", cobrancas.get(0).pagador().sobrenome());
    }

    @Test
    void cobrancaIdentificaOMesNaDescricaoEUsaOIdDoPagamentoComoReferencia() {
        service.gerarCobranca(pix("2026-12"), locatario);

        assertTrue(cobrancas.get(0).descricao().contains("2026-12"));
        assertNotNull(cobrancas.get(0).pagamentoId());
    }

    @Test
    void quemNaoParticipaDoContratoNaoGeraCobranca() {
        assertThrows(RegraDeNegocioException.class, () -> service.gerarCobranca(pix("2026-12"), estranho));

        assertTrue(cobrancas.isEmpty());
        assertTrue(banco.isEmpty());
    }

    @Test
    void cartaoSemTokenEhRecusadoAntesDeChamarOGateway() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerarCobranca(cartao(MetodoPagamento.CARTAO_CREDITO, " ", "visa", 1), locatario));

        assertTrue(erro.getMessage().contains("tokenCartao"));
        assertTrue(cobrancas.isEmpty());
    }

    @Test
    void cartaoSemBandeiraEhRecusadoAntesDeChamarOGateway() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerarCobranca(cartao(MetodoPagamento.CARTAO_CREDITO, "tok", null, 1), locatario));

        assertTrue(erro.getMessage().contains("bandeiraCartao"));
        assertTrue(cobrancas.isEmpty());
    }

    @Test
    void debitoNaoPermiteParcelar() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerarCobranca(cartao(MetodoPagamento.CARTAO_DEBITO, "tok", "debmaster", 2), locatario));

        assertTrue(erro.getMessage().contains("debito"));
        assertTrue(cobrancas.isEmpty());
    }

    @Test
    void creditoParceladoEhAceitoEOGatewayRecebeAsParcelas() {
        service.gerarCobranca(cartao(MetodoPagamento.CARTAO_CREDITO, "tok", "visa", 6), locatario);

        assertEquals(6, cobrancas.get(0).parcelas());
        assertEquals("tok", cobrancas.get(0).tokenCartao());
    }

    @Test
    void pixNaoExigeDadosDeCartao() {
        service.gerarCobranca(pix("2026-12"), locatario);

        assertNull(cobrancas.get(0).tokenCartao());
    }

    @Test
    void mesJaPagoNaoPodeSerCobradoDeNovo() {
        service.gerarCobranca(pix("2026-12"), locatario);

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.gerarCobranca(pix("2026-12"), locatario));

        assertTrue(erro.getMessage().contains("ja esta pago"));
        assertEquals(1, cobrancas.size());
    }

    @Test
    void cobrancaEmProcessamentoBloqueiaUmaSegunda() {
        respostaDaCriacao = resposta("TX-1", StatusPagamento.PROCESSANDO);
        service.gerarCobranca(pix("2026-12"), locatario);

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.gerarCobranca(pix("2026-12"), locatario));

        assertTrue(erro.getMessage().contains("em processamento"));
    }

    @Test
    void pagamentoRecusadoPodeSerTentadoDeNovoReaproveitandoALinha() {
        respostaDaCriacao = resposta("TX-1", StatusPagamento.RECUSADO);
        Pagamento primeiro = service.gerarCobranca(pix("2026-12"), locatario);

        respostaDaCriacao = resposta("TX-2", StatusPagamento.PAGO);
        Pagamento segundo = service.gerarCobranca(pix("2026-12"), locatario);

        assertEquals(primeiro.getId(), segundo.getId());
        assertEquals(1, banco.size());
        assertEquals(StatusPagamento.PAGO, segundo.getStatus());
        assertEquals("TX-2", segundo.getIdTransacaoGateway());
    }

    @Test
    void novaTentativaLimpaOsDadosDaAnterior() {
        respostaDaCriacao = new RespostaCobranca("TX-1", StatusPagamento.RECUSADO, "cc_rejected", "http://link",
                "pix-antigo", null, "linha-antiga", null, null);
        service.gerarCobranca(pix("2026-12"), locatario);

        respostaDaCriacao = resposta("TX-2", StatusPagamento.PENDENTE);
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertNull(pagamento.getLinkPagamento());
        assertNull(pagamento.getPixCopiaECola());
        assertNull(pagamento.getLinhaDigitavel());
    }

    @Test
    void pagamentoAprovadoGravaADataEPublicaOEventoUmaVez() {
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertNotNull(pagamento.getPagoEm());
        assertEquals(1, eventos.size());
        var evento = (PagamentoConfirmadoEvento) eventos.get(0);
        assertEquals(pagamento.getId(), evento.pagamentoId());
        assertEquals(locatario.getEmail(), evento.emailLocatario());
        assertEquals(locador.getEmail(), evento.emailLocador());
    }

    @Test
    void pagamentoPendenteOuRecusadoNaoPublicaEvento() {
        respostaDaCriacao = resposta("TX-1", StatusPagamento.PENDENTE);
        service.gerarCobranca(pix("2026-12"), locatario);
        respostaDaCriacao = resposta("TX-2", StatusPagamento.RECUSADO);
        service.gerarCobranca(pix("2027-01"), locatario);

        assertTrue(eventos.isEmpty());
    }

    @Test
    void sincronizarQuePassaDePendenteParaPagoPublicaEventoSoNaPrimeiraVez() {
        respostaDaCriacao = resposta("TX-1", StatusPagamento.PENDENTE);
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        service.sincronizarComGateway(pagamento.getId(), locatario);
        service.sincronizarComGateway(pagamento.getId(), locatario);

        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertEquals(1, eventos.size());
    }

    @Test
    void sincronizarSemCobrancaNoGatewayEhRecusado() {
        respostaDaCriacao = new RespostaCobranca(null, StatusPagamento.RECUSADO, "erro", null, null, null, null, null, null);
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertThrows(RegraDeNegocioException.class, () -> service.sincronizarComGateway(pagamento.getId(), locatario));
    }

    @Test
    void sincronizarPorQuemNaoParticipaEhRecusado() {
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertThrows(RegraDeNegocioException.class, () -> service.sincronizarComGateway(pagamento.getId(), estranho));
    }

    @Test
    void notificacaoDoGatewayAtualizaOPagamentoEPublicaEvento() {
        respostaDaCriacao = resposta("TX-1", StatusPagamento.PENDENTE);
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        service.processarNotificacao("TX-1");
        service.processarNotificacao("TX-1");

        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertEquals(1, eventos.size());
    }

    @Test
    void notificacaoDeTransacaoDesconhecidaEhIgnorada() {
        service.processarNotificacao("NAO-EXISTE");

        assertTrue(eventos.isEmpty());
        assertTrue(banco.isEmpty());
    }

    @Test
    void consultaQueNaoTrazDadosDaCobrancaNaoApagaOQueJaExistia() {
        respostaDaCriacao = new RespostaCobranca("TX-1", StatusPagamento.PENDENTE, "pending", "http://link",
                "pix-copia-e-cola", null, null, null, null);
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        service.sincronizarComGateway(pagamento.getId(), locatario);

        assertEquals("http://link", pagamento.getLinkPagamento());
        assertEquals("pix-copia-e-cola", pagamento.getPixCopiaECola());
    }

    @Test
    void estornoSoValeParaPagamentoConfirmado() {
        respostaDaCriacao = resposta("TX-1", StatusPagamento.PENDENTE);
        Pagamento pendente = service.gerarCobranca(pix("2026-12"), locatario);

        assertThrows(RegraDeNegocioException.class, () -> service.estornar(pendente.getId(), locatario));
        assertTrue(estornos.isEmpty());
    }

    @Test
    void estornoDePagamentoConfirmadoChamaOGatewayEMarcaComoEstornado() {
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        service.estornar(pagamento.getId(), locatario);

        assertEquals(List.of("TX-1"), estornos);
        assertEquals(StatusPagamento.ESTORNADO, pagamento.getStatus());
    }

    @Test
    void estornoPorQuemNaoParticipaEhRecusado() {
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertThrows(RegraDeNegocioException.class, () -> service.estornar(pagamento.getId(), estranho));

        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertTrue(estornos.isEmpty());
    }

    @Test
    void buscarPagamentoInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarPorId(999L));
    }

    @Test
    void detalharExigeSerParticipante() {
        Pagamento pagamento = service.gerarCobranca(pix("2026-12"), locatario);

        assertEquals(pagamento, service.detalhar(pagamento.getId(), locador));
        assertThrows(RegraDeNegocioException.class, () -> service.detalhar(pagamento.getId(), estranho));
    }
}
