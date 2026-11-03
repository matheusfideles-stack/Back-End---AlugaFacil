package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.manutencao.Manutencao;
import com.larenegocios.alugafacil.domain.manutencao.ManutencaoRepository;
import com.larenegocios.alugafacil.domain.manutencao.StatusManutencao;
import com.larenegocios.alugafacil.domain.manutencao.UrgenciaManutencao;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroManutencao;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManutencaoServiceTest {

    private final Usuario locador = Dobles.usuario(1, Perfil.LOCADOR);
    private final Usuario locatario = Dobles.usuario(2, Perfil.LOCATARIO);
    private final Usuario estranho = Dobles.usuario(3, Perfil.LOCATARIO);
    private final Usuario admin = Dobles.usuario(4, Perfil.ADMIN);

    private Contrato contrato;
    private final List<Manutencao> salvas = new ArrayList<>();
    private ManutencaoService service;

    @BeforeEach
    void montar() {
        salvas.clear();
        contrato = new Contrato();
        contrato.setId(50L);
        contrato.setImovel(Dobles.imovel(10, locador));
        contrato.setLocatario(locatario);
        contrato.setStatus(StatusContrato.ATIVO);

        ContratoService contratoService = new ContratoService();
        ReflectionTestUtils.setField(contratoService, "contratoRepository", Dobles.repositorio(ContratoRepository.class)
                .responde("findById", a -> a[0].equals(50L) ? Optional.of(contrato) : Optional.empty())
                .criar());

        service = new ManutencaoService();
        ReflectionTestUtils.setField(service, "contratoService", contratoService);
        ReflectionTestUtils.setField(service, "manutencaoRepository", Dobles.repositorio(ManutencaoRepository.class)
                .responde("save", a -> {
                    Manutencao manutencao = (Manutencao) a[0];
                    manutencao.setId(1L);
                    salvas.add(manutencao);
                    return manutencao;
                })
                .responde("findById", a -> salvas.stream().filter(m -> m.getId().equals(a[0])).findFirst())
                .criar());
    }

    private DadosCadastroManutencao pedido() {
        return new DadosCadastroManutencao(50L, "  Hidraulica ", UrgenciaManutencao.values()[0], "  Vazamento na pia da cozinha  ");
    }

    @Test
    void locatarioSolicitaEComecaComoAberta() {
        Manutencao manutencao = service.solicitar(pedido(), locatario);

        assertEquals(StatusManutencao.ABERTA, manutencao.getStatus());
        assertEquals("Hidraulica", manutencao.getTipo());
        assertEquals("Vazamento na pia da cozinha", manutencao.getDescricao());
        assertEquals(contrato, manutencao.getContrato());
    }

    @Test
    void locadorNaoPodeSolicitarManutencaoDoProprioImovel() {
        var erro = assertThrows(RegraDeNegocioException.class, () -> service.solicitar(pedido(), locador));

        assertTrue(erro.getMessage().contains("locatario"));
        assertTrue(salvas.isEmpty());
    }

    @Test
    void outroLocatarioNaoPodeSolicitar() {
        assertThrows(RegraDeNegocioException.class, () -> service.solicitar(pedido(), estranho));
    }

    @Test
    void contratoEncerradoNaoAceitaSolicitacao() {
        contrato.setStatus(StatusContrato.ENCERRADO);

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.solicitar(pedido(), locatario));

        assertTrue(erro.getMessage().contains("nao esta ativo"));
    }

    @Test
    void contratoInexistenteDaNaoEncontrado() {
        var dados = new DadosCadastroManutencao(999L, "Eletrica", UrgenciaManutencao.values()[0], "Tomada sem energia");

        assertThrows(RecursoNaoEncontradoException.class, () -> service.solicitar(dados, locatario));
    }

    @Test
    void locadorAtualizaOStatus() {
        service.solicitar(pedido(), locatario);

        Manutencao atualizada = service.atualizarStatus(1L, StatusManutencao.EM_ANDAMENTO, locador);

        assertEquals(StatusManutencao.EM_ANDAMENTO, atualizada.getStatus());
    }

    @Test
    void adminAtualizaOStatus() {
        service.solicitar(pedido(), locatario);

        assertEquals(StatusManutencao.CONCLUIDA, service.atualizarStatus(1L, StatusManutencao.CONCLUIDA, admin).getStatus());
    }

    @Test
    void locatarioNaoMudaOStatusDaPropriaSolicitacao() {
        Manutencao manutencao = service.solicitar(pedido(), locatario);

        assertThrows(RegraDeNegocioException.class, () -> service.atualizarStatus(1L, StatusManutencao.CONCLUIDA, locatario));

        assertEquals(StatusManutencao.ABERTA, manutencao.getStatus());
    }

    @Test
    void atualizarSolicitacaoInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.atualizarStatus(77L, StatusManutencao.CONCLUIDA, locador));
    }
}
