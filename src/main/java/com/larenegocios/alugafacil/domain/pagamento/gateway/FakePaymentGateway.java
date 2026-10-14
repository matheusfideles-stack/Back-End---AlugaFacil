package com.larenegocios.alugafacil.domain.pagamento.gateway;

import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "pagamento.gateway.provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao) {
        String idFake = "FAKE-" + UUID.randomUUID();
        return new RespostaCobranca(
                idFake,
                StatusPagamento.PAGO,
                "accredited",
                "https://pagamentos.alugafacil.local/checkout/" + idFake,
                solicitacao.metodo() == com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento.PIX
                        ? "00020126FAKE" + idFake : null,
                null,
                solicitacao.metodo() == com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento.BOLETO
                        ? "23790001234567890000000000000000000000000000" : null,
                null,
                LocalDateTime.now().plusDays(1)
        );
    }

    @Override
    public RespostaCobranca consultarCobranca(String idTransacaoGateway) {
        return new RespostaCobranca(idTransacaoGateway, StatusPagamento.PAGO, "accredited",
                null, null, null, null, null, null);
    }

    @Override
    public void estornarCobranca(String idTransacaoGateway) {
    }
}
