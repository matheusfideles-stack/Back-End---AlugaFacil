package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.FotoImovel;
import com.larenegocios.alugafacil.domain.imovel.FotoImovelRepository;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import com.larenegocios.alugafacil.infra.storage.ArmazenamentoDeFotos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FotoImovelServiceTest {

    // Grava "em memoria": guarda so os nomes, para o teste ver o que foi gravado e apagado.
    static class ArmazenamentoFalso extends ArmazenamentoDeFotos {

        final List<String> gravados = new ArrayList<>();
        final List<String> removidos = new ArrayList<>();
        int falharNoNumero = -1;

        ArmazenamentoFalso() {
            super("./nao-usado");
        }

        @Override
        public FotoGuardada salvar(MultipartFile arquivo) {
            if (gravados.size() == falharNoNumero) {
                throw new RegraDeNegocioException("Formato nao suportado");
            }
            String nome = "arquivo-" + (gravados.size() + 1) + ".jpg";
            gravados.add(nome);
            return new FotoGuardada(nome, "image/jpeg", arquivo.getSize());
        }

        @Override
        public void remover(String nome) {
            removidos.add(nome);
        }
    }

    private final Usuario dono = Dobles.usuario(1, Perfil.LOCADOR);
    private final Usuario outro = Dobles.usuario(2, Perfil.LOCADOR);

    private final List<FotoImovel> fotos = new ArrayList<>();
    private final ArmazenamentoFalso armazenamento = new ArmazenamentoFalso();
    private FotoImovelService service;

    private static MockMultipartFile foto(String nome) {
        return new MockMultipartFile("fotos", nome, "image/jpeg", new byte[]{1, 2, 3});
    }

    @BeforeEach
    void montar() {
        fotos.clear();
        armazenamento.gravados.clear();
        armazenamento.removidos.clear();
        armazenamento.falharNoNumero = -1;

        Imovel imovel = Dobles.imovel(10, dono);
        ImovelService imovelService = new ImovelService();
        ReflectionTestUtils.setField(imovelService, "imovelRepository", Dobles.repositorio(ImovelRepository.class)
                .responde("findById", a -> a[0].equals(10L) ? Optional.of(imovel) : Optional.empty())
                .criar());

        FotoImovelRepository repositorio = Dobles.repositorio(FotoImovelRepository.class)
                .responde("countByImovelId", a -> (long) fotos.size())
                .responde("maiorOrdem", a -> fotos.stream().mapToInt(FotoImovel::getOrdem).max().orElse(-1))
                .responde("menorOrdem", a -> fotos.stream().mapToInt(FotoImovel::getOrdem).min().orElse(0))
                .responde("save", a -> {
                    FotoImovel foto = (FotoImovel) a[0];
                    foto.setId(fotos.size() + 1L);
                    fotos.add(foto);
                    return foto;
                })
                .responde("delete", a -> fotos.remove(a[0]))
                .responde("findByIdAndImovelId", a -> fotos.stream().filter(f -> f.getId().equals(a[0])).findFirst())
                .responde("findByImovelIdOrderByOrdemAscIdAsc", a -> List.copyOf(fotos))
                .criar();

        service = new FotoImovelService();
        ReflectionTestUtils.setField(service, "fotoRepository", repositorio);
        ReflectionTestUtils.setField(service, "imovelService", imovelService);
        ReflectionTestUtils.setField(service, "armazenamento", armazenamento);
    }

    @Test
    void donoAdicionaFotosNaOrdemEnviada() {
        List<FotoImovel> salvas = service.adicionar(10L, List.of(foto("a.jpg"), foto("b.jpg")), dono);

        assertEquals(2, salvas.size());
        assertEquals(0, salvas.get(0).getOrdem());
        assertEquals(1, salvas.get(1).getOrdem());
    }

    @Test
    void novasFotosVamParaDepoisDasQueJaExistem() {
        service.adicionar(10L, List.of(foto("a.jpg")), dono);

        List<FotoImovel> novas = service.adicionar(10L, List.of(foto("b.jpg")), dono);

        assertEquals(1, novas.get(0).getOrdem());
    }

    @Test
    void outroLocadorNaoAdicionaFotosENadaEhGravado() {
        assertThrows(RegraDeNegocioException.class, () -> service.adicionar(10L, List.of(foto("a.jpg")), outro));

        assertTrue(armazenamento.gravados.isEmpty());
    }

    @Test
    void semArquivosOuSoComArquivosVaziosEhRecusado() {
        assertThrows(RegraDeNegocioException.class, () -> service.adicionar(10L, null, dono));
        assertThrows(RegraDeNegocioException.class, () -> service.adicionar(10L, List.of(), dono));
        var vazio = new MockMultipartFile("fotos", "vazio.jpg", "image/jpeg", new byte[0]);
        assertThrows(RegraDeNegocioException.class, () -> service.adicionar(10L, List.of(vazio), dono));
    }

    @Test
    void limiteDeDezFotosPorImovelValeParaOTotalNaoSoParaCadaEnvio() {
        List<MultipartFile> oito = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            oito.add(foto("f" + i + ".jpg"));
        }
        service.adicionar(10L, oito, dono);

        var erro = assertThrows(RegraDeNegocioException.class,
                () -> service.adicionar(10L, List.of(foto("x.jpg"), foto("y.jpg"), foto("z.jpg")), dono));

        assertTrue(erro.getMessage().contains("10 fotos"));
        assertEquals(8, fotos.size());
        assertEquals(8, armazenamento.gravados.size());
    }

    @Test
    void exatamenteDezFotosEhPermitido() {
        List<MultipartFile> dez = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dez.add(foto("f" + i + ".jpg"));
        }

        assertEquals(10, service.adicionar(10L, dez, dono).size());
    }

    @Test
    void seUmaFotoFalhaAsJaGravadasNoDiscoSaoApagadas() {
        armazenamento.falharNoNumero = 1;

        assertThrows(RegraDeNegocioException.class,
                () -> service.adicionar(10L, List.of(foto("a.jpg"), foto("b.jpg")), dono));

        assertEquals(List.of("arquivo-1.jpg"), armazenamento.removidos);
    }

    @Test
    void definirCapaPoeAFotoNaFrenteDasOutras() {
        service.adicionar(10L, List.of(foto("a.jpg"), foto("b.jpg"), foto("c.jpg")), dono);
        FotoImovel terceira = fotos.get(2);

        service.definirCapa(10L, terceira.getId(), dono);

        assertTrue(terceira.getOrdem() < fotos.get(0).getOrdem());
    }

    @Test
    void outroLocadorNaoMudaACapa() {
        service.adicionar(10L, List.of(foto("a.jpg"), foto("b.jpg")), dono);
        int ordemAntes = fotos.get(1).getOrdem();

        assertThrows(RegraDeNegocioException.class, () -> service.definirCapa(10L, fotos.get(1).getId(), outro));

        assertEquals(ordemAntes, fotos.get(1).getOrdem());
    }

    @Test
    void removerApagaORegistroEOArquivoDoDisco() {
        service.adicionar(10L, List.of(foto("a.jpg")), dono);

        service.remover(10L, fotos.get(0).getId(), dono);

        assertTrue(fotos.isEmpty());
        assertEquals(List.of("arquivo-1.jpg"), armazenamento.removidos);
    }

    @Test
    void outroLocadorNaoRemoveFotoENadaEhApagado() {
        service.adicionar(10L, List.of(foto("a.jpg")), dono);

        assertThrows(RegraDeNegocioException.class, () -> service.remover(10L, fotos.get(0).getId(), outro));

        assertEquals(1, fotos.size());
        assertTrue(armazenamento.removidos.isEmpty());
    }

    @Test
    void fotoInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(10L, 99L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.remover(10L, 99L, dono));
    }

    @Test
    void listarFotosDeImovelInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.listar(404L));
    }
}
