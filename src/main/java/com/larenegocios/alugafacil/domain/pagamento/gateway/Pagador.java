package com.larenegocios.alugafacil.domain.pagamento.gateway;

public record Pagador(
        String nome,
        String sobrenome,
        String email,
        String cpf,
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf
) {
}
