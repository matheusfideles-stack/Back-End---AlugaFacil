package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.imovel.Comodidade;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import com.larenegocios.alugafacil.domain.imovel.TipoImovel;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroImovel;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.geocodificacao.Coordenadas;
import com.larenegocios.alugafacil.infra.geocodificacao.Geocodificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImovelServiceTest {

    private final Usuario dono = Dobles.usuario(1, Perfil.LOCADOR);
    private final Usuario outroLocador = Dobles.usuario(2, Perfil.LOCADOR);
    private final Usuario admin = Dobles.usuario(3, Perfil.ADMIN);

    private Imovel existente;
    private boolean temContratoAtivo;
    private ImovelService service;
    private final List<String> consultas = new ArrayList<>();
    private Optional<Coordenadas> resposta = Optional.of(new Coordenadas(-18.6, -46.5));
    private boolean geocodificadorFalha;

    @BeforeEach
    void montar() {
        existente = Dobles.imovel(10, dono);
        temContratoAtivo = false;
        consultas.clear();
        resposta = Optional.of(new Coordenadas(-18.6, -46.5));
        geocodificadorFalha = false;

        service = new ImovelService();
        ReflectionTestUtils.setField(service, "imovelRepository", Dobles.repositorio(ImovelRepository.class)
                .responde("save", a -> a[0])
                .responde("findById", a -> a[0].equals(10L) ? Optional.of(existente) : Optional.empty())
                .criar());
        Geocodificador geocodificador = (logradouro, numero, bairro, cidade, uf) -> {
            consultas.add(String.join("|", logradouro, numero, bairro, cidade, uf));
            if (geocodificadorFalha) {
                throw new IllegalStateException("sem internet");
            }
            return resposta;
        };
        ReflectionTestUtils.setField(service, "geocodificador", geocodificador);
        ReflectionTestUtils.setField(service, "contratoRepository", Dobles.repositorio(ContratoRepository.class)
                .responde("existsByImovelIdAndStatus", a -> temContratoAtivo)
                .criar());
    }

    private DadosCadastroImovel dados(BigDecimal condominio) {
        return new DadosCadastroImovel("  Casa ampla  ", "Descricao", TipoImovel.values()[0], " Rua A ", " 12 ", null,
                " Centro ", " Recife ", "pe", " 50000-000 ", new BigDecimal("2000.00"), condominio, 3, 2, 1, 120, null);
    }

    @Test
    void cadastrarGuardaOLocadorEDeixaDisponivel() {
        Imovel imovel = service.cadastrar(dados(new BigDecimal("250.00")), dono);

        assertEquals(dono, imovel.getLocador());
        assertTrue(imovel.getDisponivel());
    }

    @Test
    void cadastrarLimpaEspacosEPoeUfEmMaiuscula() {
        Imovel imovel = service.cadastrar(dados(null), dono);

        assertEquals("Casa ampla", imovel.getTitulo());
        assertEquals("Rua A", imovel.getLogradouro());
        assertEquals("12", imovel.getNumero());
        assertEquals("Centro", imovel.getBairro());
        assertEquals("Recife", imovel.getCidade());
        assertEquals("PE", imovel.getUf());
        assertEquals("50000-000", imovel.getCep());
    }

    @Test
    void condominioEmBrancoViraZero() {
        Imovel imovel = service.cadastrar(dados(null), dono);

        assertEquals(BigDecimal.ZERO, imovel.getValorCondominio());
    }

    @Test
    void atualizarPeloDonoAlteraOsDados() {
        Imovel atualizado = service.atualizar(10L, dados(new BigDecimal("100.00")), dono);

        assertEquals("Casa ampla", atualizado.getTitulo());
        assertEquals(new BigDecimal("2000.00"), atualizado.getValorAluguel());
    }

    @Test
    void atualizarPorOutroLocadorEhRecusadoESemEfeito() {
        String tituloAntes = existente.getTitulo();

        assertThrows(RegraDeNegocioException.class, () -> service.atualizar(10L, dados(null), outroLocador));

        assertEquals(tituloAntes, existente.getTitulo());
    }

    @Test
    void adminPodeAtualizarImovelDeOutraPessoa() {
        service.atualizar(10L, dados(null), admin);

        assertEquals("Casa ampla", existente.getTitulo());
    }

    @Test
    void buscarImovelInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarPorId(404L));
    }

    @Test
    void desativarTiraDaListaDeDisponiveis() {
        service.desativar(10L, dono);

        assertFalse(existente.getDisponivel());
    }

    @Test
    void desativarPorOutroLocadorEhRecusado() {
        assertThrows(RegraDeNegocioException.class, () -> service.desativar(10L, outroLocador));

        assertTrue(existente.getDisponivel());
    }

    @Test
    void reativarVoltaADisponivel() {
        existente.setDisponivel(false);

        service.reativar(10L, dono);

        assertTrue(existente.getDisponivel());
    }

    @Test
    void naoReativaImovelComContratoAtivo() {
        existente.setDisponivel(false);
        temContratoAtivo = true;

        var erro = assertThrows(RegraDeNegocioException.class, () -> service.reativar(10L, dono));

        assertTrue(erro.getMessage().contains("contrato ativo"));
        assertFalse(existente.getDisponivel());
    }

private DadosCadastroImovel dadosComComodidades(Set<Comodidade> comodidades, String rua) {
        return new DadosCadastroImovel("Casa ampla", "Descricao", TipoImovel.values()[0], rua, "12", null,
                "Centro", "Patos de Minas", "MG", "38700-000", new BigDecimal("2000.00"), null, 3, 2, 1, 120, comodidades);
    }

    @Test
    void cadastrarGuardaAsComodidadesEscolhidas() {
        Imovel imovel = service.cadastrar(dadosComComodidades(Set.of(Comodidade.PISCINA, Comodidade.TV), "Rua A"), dono);

        assertEquals(Set.of(Comodidade.PISCINA, Comodidade.TV), imovel.getComodidades());
    }

    @Test
    void cadastrarSemComodidadesDeixaAListaVazia() {
        assertTrue(service.cadastrar(dados(null), dono).getComodidades().isEmpty());
    }

    @Test
    void atualizarTrocaAsComodidades() {
        service.atualizar(10L, dadosComComodidades(Set.of(Comodidade.LAREIRA), "Rua A"), dono);
        service.atualizar(10L, dadosComComodidades(Set.of(Comodidade.AQUECEDOR, Comodidade.CHURRASQUEIRA), "Rua A"), dono);

        assertEquals(Set.of(Comodidade.AQUECEDOR, Comodidade.CHURRASQUEIRA), existente.getComodidades());
    }

    @Test
    void atualizarSemEnviarComodidadesNaoApagaAsQueJaExistiam() {
        existente.setComodidades(new java.util.LinkedHashSet<>(Set.of(Comodidade.PISCINA)));

        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);

        assertEquals(Set.of(Comodidade.PISCINA), existente.getComodidades());
    }

    @Test
    void atualizarComListaVaziaRemoveTodasAsComodidades() {
        existente.setComodidades(new java.util.LinkedHashSet<>(Set.of(Comodidade.PISCINA)));

        service.atualizar(10L, dadosComComodidades(Set.of(), "Rua A"), dono);

        assertTrue(existente.getComodidades().isEmpty());
    }

    @Test
    void cadastrarGuardaAPosicaoEncontradaNoMapa() {
        Imovel imovel = service.cadastrar(dados(null), dono);

        assertEquals(-18.6, imovel.getLatitude());
        assertEquals(-46.5, imovel.getLongitude());
        assertEquals(List.of("Rua A|12|Centro|Recife|PE"), consultas);
    }

    @Test
    void enderecoNaoEncontradoSalvaOImovelSemPosicao() {
        resposta = Optional.empty();

        Imovel imovel = service.cadastrar(dados(null), dono);

        assertEquals(null, imovel.getLatitude());
        assertEquals(null, imovel.getLongitude());
    }

    @Test
    void falhaDoGeocodificadorNuncaImpedeSalvarOImovel() {
        geocodificadorFalha = true;

        Imovel imovel = service.cadastrar(dados(null), dono);

        assertEquals("Casa ampla", imovel.getTitulo());
        assertEquals(null, imovel.getLatitude());
    }

    @Test
    void mudarSoOPrecoNaoConsultaOMapaDeNovo() {
        existente.setLatitude(-18.6);
        existente.setLongitude(-46.5);
        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);
        consultas.clear();

        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);

        assertTrue(consultas.isEmpty());
    }

    @Test
    void mudarOEnderecoConsultaOMapaEAtualizaAPosicao() {
        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);
        resposta = Optional.of(new Coordenadas(-18.7, -46.6));

        service.atualizar(10L, dadosComComodidades(null, "Rua B"), dono);

        assertEquals(-18.7, existente.getLatitude());
        assertEquals(-46.6, existente.getLongitude());
    }

    @Test
    void enderecoNovoNaoEncontradoApagaAPosicaoAntigaQueEstariaErrada() {
        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);
        resposta = Optional.empty();

        service.atualizar(10L, dadosComComodidades(null, "Rua B"), dono);

        assertEquals(null, existente.getLatitude());
    }

    @Test
    void imovelAindaSemPosicaoEConsultadoNaProximaAtualizacao() {
        resposta = Optional.empty();
        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);
        resposta = Optional.of(new Coordenadas(-18.8, -46.7));

        service.atualizar(10L, dadosComComodidades(null, "Rua A"), dono);

        assertEquals(-18.8, existente.getLatitude());
    }

    @Test
    void localizarPendenteSoMexeEmImovelSemPosicao() {
        existente.setLatitude(-1.0);
        existente.setLongitude(-2.0);

        service.localizarPendente(10L);

        assertTrue(consultas.isEmpty());
        assertEquals(-1.0, existente.getLatitude());
    }

    @Test
    void localizarPendenteCompletaAPosicaoDoImovelAntigo() {
        service.localizarPendente(10L);

        assertEquals(-18.6, existente.getLatitude());
    }
}
