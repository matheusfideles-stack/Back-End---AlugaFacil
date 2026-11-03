package com.larenegocios.alugafacil.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DadosCadastroVisita(

        @NotNull
        @Future(message = "escolha uma data e hora futuras")
        LocalDateTime dataHora,

        @Size(max = 500, message = "a mensagem pode ter no maximo 500 caracteres")
        String mensagem) {
}
