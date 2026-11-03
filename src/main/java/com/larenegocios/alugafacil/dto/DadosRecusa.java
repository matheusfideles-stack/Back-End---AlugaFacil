package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Size;

public record DadosRecusa(
        @Size(max = 300, message = "o motivo pode ter no maximo 300 caracteres")
        String motivo) {
}
