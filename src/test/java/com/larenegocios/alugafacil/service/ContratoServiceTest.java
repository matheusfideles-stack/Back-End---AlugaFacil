package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroContrato;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContratoServiceTest {

    private final Usuario locador = Dobles.usuario(1, Perfil.LOCADOR);
    private final Usuario locatario = Dobles.usuario(2, Perfil.LOCATARIO);
    private final Usuario outraPessoa = Dobles.usuario(3, Perfil.LOCATARIO);
    private final Usuario admin = Dobles.usuario(4, Perfil.ADMIN);

    private Imovel imovel;
    private boolean imovelTemContratoAtivo;
    private final List<Contrato> salvos = new ArrayList<>();
    private ContratoService service;

    @BeforeEach
    void montar() {
        imovel = Dobles.imovel(10, locador);
        imovelTemContratoAtivo = false;
        salvos.clear();

        ImovelService imovelService = new ImovelService();
        ReflectionTestUtils.setField(imovelService, "imovelRepository",
                Dobles.repositorio(com.larenegocios.alugafacil.domain.imovel.ImovelRepository.class)
                        .responde("findById", a -> a[0].equals(10L) ? Optional.of(imovel) : Optional.empty())
                        .criar());

        ContratoRepository repositorio = Dobles.repositorio(ContratoRepository.class)
                .responde("existsByImovelIdAndStatus", a -> imovelTemContratoAtivo)
                .responde("save", a -> {
                    Contrato contrato = (Contrato) a[0];
                    contrato.setId(100L);
                    salvos.add(contrato);
                    return contrato;
                })
                .responde("findById", a -> salvos.stream().filter(c -> c.getId().equals(a[0])).findFirst())
                .criar();

        service = new ContratoService();
        ReflectionTestUtils.setField(service, "contratoRepository", repositorio);
        ReflectionTestUtils.setField(service, "imovelService", imovelService);
    }

    private DadosCadastroContrato dados(LocalDate inicio, LocalDate fim) {
        return new DadosCadastroContrato(10L, inicio, fim, 5);
    }

    private DadosCadastroContrato dadosValidos() {
        return dados(LocalDate.now(), LocalDate.now().plusMonths(12));
    }

    @Test
    void firmarCalculaValorMensalSomandoAluguelECondominio() {
        Contrato contrato = service.firmar(dadosValidos(), locatario);

        assertEquals(new BigDecimal("1800.00"), contrato.getValorMensal());
        assertEquals(StatusContrato.ATIVO, contrato.getStatus());
        assertEquals(locatario, contrato.getLocatario());
        assertEquals(5, contrato.getDiaVencimento());
    }

    @Test
    void firmarTiraOImovelDaListaDeDisponiveis() {
        service.firmar(dadosValidos(), locatario);

        assertFalse(imovel.getDisponivel());
    }

    @Test
    void firmarRecusaImovelIndisponivel() {
        imovel.setDisponivel(false);

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.firmar(dadosValidos(), locatario));

        assertTrue(erro.getMessage().contains("nao esta disponivel"));
        assertTrue(salvos.isEmpty());
    }

    @Test
    void firmarRecusaQuandoOLocadorTentaAlugarOProprioImovel() {
        var erro = assertThrows(RegraDeNegocioException.class, () -> service.firmar(dadosValidos(), locador));

        assertTrue(erro.getMessage().contains("proprio imovel"));
        assertTrue(salvos.isEmpty());
    }

    @Test
    void firmarRecusaImovelQueJaTemContratoAtivo() {
        imovelTemContratoAtivo = true;

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.firmar(dadosValidos(), locatario));

        assertTrue(erro.getMessage().contains("contrato ativo"));
    }

    @Test
    void firmarRecusaDataFimIgualOuAnteriorAoInicio() {
        LocalDate dia = LocalDate.now().plusDays(10);

        assertThrows(RegraDeNegocioException.class, () -> service.firmar(dados(dia, dia), locatario));
        assertThrows(RegraDeNegocioException.class, () -> service.firmar(dados(dia, dia.minusDays(1)), locatario));
        assertTrue(salvos.isEmpty());
    }

    @Test
    void firmarImovelInexistenteDaNaoEncontrado() {
        var dados = new DadosCadastroContrato(999L, LocalDate.now(), LocalDate.now().plusMonths(6), 5);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.firmar(dados, locatario));
    }

    @Test
    void encerrarMarcaContratoEncerradoELiberaOImovel() {
        Contrato contrato = service.firmar(dadosValidos(), locatario);

        service.encerrar(contrato.getId(), locador);

        assertEquals(StatusContrato.ENCERRADO, contrato.getStatus());
        assertTrue(imovel.getDisponivel());
    }

    @Test
    void encerrarPorQuemNaoParticipaEhRecusadoESemEfeito() {
        Contrato contrato = service.firmar(dadosValidos(), locatario);

        assertThrows(RegraDeNegocioException.class, () -> service.encerrar(contrato.getId(), outraPessoa));

        assertEquals(StatusContrato.ATIVO, contrato.getStatus());
        assertFalse(imovel.getDisponivel());
    }

    @Test
    void locatarioLocadorEAdminParticipamDoContrato() {
        Contrato contrato = service.firmar(dadosValidos(), locatario);

        service.validarParticipante(contrato, locatario);
        service.validarParticipante(contrato, locador);
        service.validarParticipante(contrato, admin);
        assertThrows(RegraDeNegocioException.class, () -> service.validarParticipante(contrato, outraPessoa));
    }

    @Test
    void buscarContratoInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarPorId(404L));
    }
}
