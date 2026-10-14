package com.larenegocios.alugafacil.domain.pagamento.gateway;

/**
 * Dados do pagador exigidos pelos meios de pagamento brasileiros.
 * CPF e endereco sao obrigatorios para boleto e recomendados para Pix.
 */
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
