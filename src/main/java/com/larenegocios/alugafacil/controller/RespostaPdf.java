package com.larenegocios.alugafacil.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class RespostaPdf {

    private RespostaPdf() {
    }

    static ResponseEntity<byte[]> de(byte[] conteudo, String nomeDoArquivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nomeDoArquivo).build().toString())
                .body(conteudo);
    }
}
