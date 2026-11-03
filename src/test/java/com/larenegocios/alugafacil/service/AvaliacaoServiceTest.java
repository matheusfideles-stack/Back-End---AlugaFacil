package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.avaliacao.Avaliacao;
import com.larenegocios.alugafacil.domain.avaliacao.AvaliacaoRepository;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosCadastroAvaliacao;
import com.larenegocios.alugafacil.dto.DadosResumoAvaliacoes;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvaliacaoServiceTest {

    private final Usuario cliente = Dobles.usuario(2, Perfil.LOCATARIO);
    private final Usuario locador = Dobles.usuario(1, Perfil.LOCADOR);

    private final List<Avaliacao> guardadas = new ArrayList<>();
    private final List<Long> removidas = new ArrayList<>();
    private List<AvaliacaoRepository.ContagemPorNota> contagens = List.of();
    private Imovel imovel;
    private AvaliacaoService service;

    private static AvaliacaoRepository.ContagemPorNota contagem(int nota, long total) {
        return new AvaliacaoRepository.ContagemPorNota() {
            public Integer getNota() {
                return nota;
            }

            public Long getTotal() {
                return total;
            }
        };
    }

    @BeforeEach
    void montar() {
        guardadas.clear();
        removidas.clear();
        contagens = List.of();
        imovel = Dobles.imovel(10, locador);

        ImovelService imovelService = new ImovelService();
        ReflectionTestUtils.setField(imovelService, "imovelRepository", Dobles.repositorio(ImovelRepository.class)
                .responde("findById", a -> a[0].equals(10L) ? Optional.of(imovel) : Optional.empty())
                .criar());

        AvaliacaoRepository repositorio = Dobles.repositorio(AvaliacaoRepository.class)
                .responde("findByImovelIdAndUsuarioId", a -> guardadas.stream()
                        .filter(v -> v.getImovel().getId().equals(a[0]) && v.getUsuario().getId().equals(a[1]))
                        .findFirst())
                .responde("save", a -> {
                    Avaliacao avaliacao = (Avaliacao) a[0];
                    if (!guardadas.contains(avaliacao)) {
                        guardadas.add(avaliacao);
                    }
                    return avaliacao;
                })
                .responde("delete", a -> guardadas.remove(a[0]))
                .responde("existsById", a -> a[0].equals(7L))
                .responde("deleteById", a -> removidas.add((Long) a[0]))
                .responde("contagemPorNota", a -> contagens)
                .criar();

        service = new AvaliacaoService();
        ReflectionTestUtils.setField(service, "avaliacaoRepository", repositorio);
        ReflectionTestUtils.setField(service, "imovelService", imovelService);
    }

    @Test
    void locatarioPodeAvaliarQualquerImovel() {
        Avaliacao avaliacao = service.salvar(10L, new DadosCadastroAvaliacao(4, "Muito bom"), cliente);

        assertEquals(4, avaliacao.getNota());
        assertEquals("Muito bom", avaliacao.getComentario());
        assertEquals(cliente, avaliacao.getUsuario());
        assertEquals(imovel, avaliacao.getImovel());
    }

    @Test
    void locadorNaoPodeAvaliar() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.salvar(10L, new DadosCadastroAvaliacao(5, null), locador));

        assertTrue(erro.getMessage().contains("locatarios"));
        assertTrue(guardadas.isEmpty());
    }

    @Test
    void avaliarDeNovoAtualizaAAnteriorEmVezDeCriarOutra() {
        service.salvar(10L, new DadosCadastroAvaliacao(2, "Ruim"), cliente);
        service.salvar(10L, new DadosCadastroAvaliacao(5, "Melhorou"), cliente);

        assertEquals(1, guardadas.size());
        assertEquals(5, guardadas.get(0).getNota());
        assertEquals("Melhorou", guardadas.get(0).getComentario());
    }

    @Test
    void comentarioEmBrancoOuSoComEspacosViraNulo() {
        assertNull(service.salvar(10L, new DadosCadastroAvaliacao(3, "   "), cliente).getComentario());
        assertNull(service.salvar(10L, new DadosCadastroAvaliacao(3, null), cliente).getComentario());
    }

    @Test
    void comentarioTemOsEspacosDasPontasRemovidos() {
        Avaliacao avaliacao = service.salvar(10L, new DadosCadastroAvaliacao(3, "  gostei  "), cliente);

        assertEquals("gostei", avaliacao.getComentario());
    }

    @Test
    void avaliarImovelInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.salvar(404L, new DadosCadastroAvaliacao(3, null), cliente));
    }

    @Test
    void minhaAvaliacaoInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.minha(10L, cliente));
    }

    @Test
    void removerMinhaApagaSoAAvaliacaoDoAutor() {
        service.salvar(10L, new DadosCadastroAvaliacao(3, null), cliente);

        service.removerMinha(10L, cliente);

        assertTrue(guardadas.isEmpty());
    }

    @Test
    void removerPorIdInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.removerPorId(99L));

        service.removerPorId(7L);
        assertEquals(List.of(7L), removidas);
    }

    @Test
    void resumoSemAvaliacoesNaoTemMedia() {
        DadosResumoAvaliacoes resumo = service.resumo(10L);

        assertNull(resumo.media());
        assertEquals(0, resumo.total());
        assertArrayEquals(new long[6], resumo.distribuicao());
    }

    @Test
    void resumoCalculaMediaArredondadaEDistribuicaoPorNota() {
        // 2 notas 5, 1 nota 4, 1 nota 1 -> 5+5+4+1 = 15 / 4 = 3,75 -> 3,8
        contagens = List.of(contagem(5, 2), contagem(4, 1), contagem(1, 1));

        DadosResumoAvaliacoes resumo = service.resumo(10L);

        assertEquals(3.8, resumo.media());
        assertEquals(4, resumo.total());
        assertArrayEquals(new long[]{0, 1, 0, 0, 1, 2}, resumo.distribuicao());
    }

    @Test
    void notaZeroContaNaMedia() {
        contagens = List.of(contagem(0, 1), contagem(4, 1));

        assertEquals(2.0, service.resumo(10L).media());
    }
}
