package com.larenegocios.alugafacil.infra.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class GeradorDeToken {

    private final SecureRandom aleatorio = new SecureRandom();

    public String gerarCodigo() {
        return String.format("%06d", aleatorio.nextInt(1_000_000));
    }

    // O usuario entra no hash porque o espaco de 6 digitos e pequeno: sem isso o mesmo codigo teria o mesmo hash para todos.
    public String hash(Long usuarioId, String codigo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256")
                    .digest((usuarioId + ":" + codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean confere(String hashGuardado, Long usuarioId, String codigo) {
        return MessageDigest.isEqual(
                hashGuardado.getBytes(StandardCharsets.UTF_8),
                hash(usuarioId, codigo).getBytes(StandardCharsets.UTF_8));
    }
}
