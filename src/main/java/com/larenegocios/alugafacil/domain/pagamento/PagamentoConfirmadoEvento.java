package com.larenegocios.alugafacil.domain.pagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Os dados ja vem copiados do banco: quem recebe o evento nao precisa de transacao nem de acesso lazy.
public record PagamentoConfirmadoEvento(
        Long pagamentoId,
        BigDecimal valor,
        MetodoPagamento metodo,
        String referenciaMes,
        LocalDateTime pagoEm,
        String tituloImovel,
        String nomeLocatario,
        String emailLocatario,
        String nomeLocador,
        String emailLocador
) {
    public static PagamentoConfirmadoEvento de(Pagamento pagamento) {
        var contrato = pagamento.getContrato();
        var locatario = contrato.getLocatario();
        var locador = contrato.getImovel().getLocador();
        return new PagamentoConfirmadoEvento(
                pagamento.getId(),
                pagamento.getValor(),
                pagamento.getMetodo(),
                pagamento.getReferenciaMes(),
                pagamento.getPagoEm(),
                contrato.getImovel().getTitulo(),
                locatario.getNome(),
                locatario.getEmail(),
                locador.getNome(),
                locador.getEmail());
    }
}
