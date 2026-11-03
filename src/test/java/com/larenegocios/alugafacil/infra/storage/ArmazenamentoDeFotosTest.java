package com.larenegocios.alugafacil.infra.storage;

import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmazenamentoDeFotosTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @TempDir
    Path pasta;

    private ArmazenamentoDeFotos armazenamento;

    @BeforeEach
    void montar() {
        armazenamento = new ArmazenamentoDeFotos(pasta.toString());
    }

    private static MockMultipartFile arquivo(String nomeOriginal, String tipoInformado, byte[] bytes) {
        return new MockMultipartFile("fotos", nomeOriginal, tipoInformado, bytes);
    }

    @Test
    void reconheceJpegPngEWebpPeloConteudo() {
        assertEquals("image/jpeg", ArmazenamentoDeFotos.detectarTipo(JPEG));
        assertEquals("image/png", ArmazenamentoDeFotos.detectarTipo(PNG));
        assertEquals("image/webp", ArmazenamentoDeFotos.detectarTipo(WEBP));
    }

    @Test
    void naoReconheceOutrosFormatosNemArquivosCurtos() {
        assertNull(ArmazenamentoDeFotos.detectarTipo("GIF89a....".getBytes()));
        assertNull(ArmazenamentoDeFotos.detectarTipo("<script>alert(1)</script>".getBytes()));
        assertNull(ArmazenamentoDeFotos.detectarTipo(new byte[0]));
        assertNull(ArmazenamentoDeFotos.detectarTipo(new byte[]{(byte) 0xFF, (byte) 0xD8}));
    }

    @Test
    void salvaComNomeGeradoEExtensaoDoTipoReal() throws IOException {
        var guardada = armazenamento.salvar(arquivo("../../minha foto.exe", "application/x-msdownload", PNG));

        assertEquals("image/png", guardada.contentType());
        assertTrue(guardada.arquivo().endsWith(".png"));
        assertFalse(guardada.arquivo().contains("exe"));
        assertFalse(guardada.arquivo().contains("/"));
        assertEquals(PNG.length, guardada.tamanho());
        assertTrue(Files.isRegularFile(pasta.resolve(guardada.arquivo())));
    }

    @Test
    void ignoraOTipoQueOClienteInformaEConfiaNosBytes() {
        var guardada = armazenamento.salvar(arquivo("a.png", "image/png", JPEG));

        assertEquals("image/jpeg", guardada.contentType());
        assertTrue(guardada.arquivo().endsWith(".jpg"));
    }

    @Test
    void recusaArquivoQueNaoEImagemMesmoComNomeDeImagem() throws IOException {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> armazenamento.salvar(arquivo("foto.jpg", "image/jpeg", "nao sou imagem".getBytes())));

        assertTrue(erro.getMessage().contains("Formato nao suportado"));
        try (var arquivos = Files.list(pasta)) {
            assertEquals(0, arquivos.count());
        }
    }

    @Test
    void recusaArquivoVazio() {
        assertThrows(RegraDeNegocioException.class, () -> armazenamento.salvar(arquivo("a.jpg", "image/jpeg", new byte[0])));
    }

    @Test
    void recusaArquivoMaiorQueCincoMegabytes() {
        byte[] grande = new byte[(int) ArmazenamentoDeFotos.TAMANHO_MAXIMO + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

        var erro = assertThrows(RegraDeNegocioException.class, () -> armazenamento.salvar(arquivo("g.jpg", "image/jpeg", grande)));

        assertTrue(erro.getMessage().contains("5 MB"));
    }

    @Test
    void lerDevolveOArquivoGravado() throws IOException {
        var guardada = armazenamento.salvar(arquivo("a.jpg", "image/jpeg", JPEG));

        assertEquals(JPEG.length, armazenamento.ler(guardada.arquivo()).contentLength());
    }

    @Test
    void lerArquivoInexistenteDaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> armazenamento.ler("nao-existe.jpg"));
    }

    @Test
    void naoLeArquivosForaDaPastaDeFotos() throws IOException {
        Path fora = Files.writeString(pasta.getParent().resolve("segredo-" + System.nanoTime() + ".txt"), "segredo");
        try {
            assertThrows(RecursoNaoEncontradoException.class, () -> armazenamento.ler("../" + fora.getFileName()));
        } finally {
            Files.deleteIfExists(fora);
        }
    }

    @Test
    void removerApagaOArquivo() {
        var guardada = armazenamento.salvar(arquivo("a.jpg", "image/jpeg", JPEG));

        armazenamento.remover(guardada.arquivo());

        assertFalse(Files.exists(pasta.resolve(guardada.arquivo())));
    }

    @Test
    void removerNaoApagaArquivosForaDaPastaDeFotos() throws IOException {
        Path fora = Files.writeString(pasta.getParent().resolve("intocavel-" + System.nanoTime() + ".txt"), "dados");
        try {
            armazenamento.remover("../" + fora.getFileName());

            assertTrue(Files.exists(fora));
        } finally {
            Files.deleteIfExists(fora);
        }
    }

    @Test
    void removerArquivoQueNaoExisteNaoDaErro() {
        armazenamento.remover("nao-existe.jpg");
    }
}
