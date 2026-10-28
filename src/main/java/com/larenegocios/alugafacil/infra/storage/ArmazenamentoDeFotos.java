package com.larenegocios.alugafacil.infra.storage;

import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class ArmazenamentoDeFotos {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoDeFotos.class);

    public record FotoGuardada(String arquivo, String contentType, long tamanho) {
    }

    private final Path pasta;

    public ArmazenamentoDeFotos(@Value("${app.uploads.dir:./uploads}") String pasta) {
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
    }

    // O tipo vem dos primeiros bytes do arquivo e o nome e gerado aqui: nada do que o cliente envia define onde ou como grava.
    public FotoGuardada salvar(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Uma das fotos esta vazia");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("Cada foto pode ter no maximo 5 MB");
        }
        try {
            byte[] bytes = arquivo.getBytes();
            String tipo = detectarTipo(bytes);
            if (tipo == null) {
                throw new RegraDeNegocioException("Formato nao suportado. Envie fotos JPG, PNG ou WebP");
            }
            String nome = UUID.randomUUID() + extensaoDe(tipo);
            Files.createDirectories(pasta);
            Files.write(pasta.resolve(nome), bytes);
            return new FotoGuardada(nome, tipo, bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel gravar a foto", e);
        }
    }

    public Resource ler(String nome) {
        Path caminho = pasta.resolve(nome).normalize();
        if (!caminho.startsWith(pasta) || !Files.isRegularFile(caminho)) {
            throw new RecursoNaoEncontradoException("Arquivo da foto nao encontrado");
        }
        return new FileSystemResource(caminho);
    }

    public void remover(String nome) {
        try {
            Path caminho = pasta.resolve(nome).normalize();
            if (caminho.startsWith(pasta)) {
                Files.deleteIfExists(caminho);
            }
        } catch (IOException e) {
            log.warn("Nao foi possivel apagar o arquivo {}", nome, e);
        }
    }

    static String detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String extensaoDe(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
    }
}
