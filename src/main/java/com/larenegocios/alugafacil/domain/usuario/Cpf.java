package com.larenegocios.alugafacil.domain.usuario;

// Confere os dois digitos verificadores do CPF. Nao prova que o CPF e de quem esta se cadastrando, so que ele existe no formato.
public final class Cpf {

    private Cpf() {
    }

    public static boolean valido(String cpf) {
        if (cpf == null) {
            return false;
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digito(digitos, 9) == digitos.charAt(9) - '0' && digito(digitos, 10) == digitos.charAt(10) - '0';
    }

    private static int digito(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
