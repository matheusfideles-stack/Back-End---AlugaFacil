package com.larenegocios.alugafacil.domain.pagamento.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementacao falsa usada em desenvolvimento/testes e enquanto nenhum gateway
 * real (Mercado Pago, Stripe etc.) e integrado. Simula uma cobranca aprovada
 * instantaneamente, sem chamar nenhuma API externa, vou usar por enquanto para apresentar para voces como que podemos fazer para fiicar mais facil para todos
 * <p>
 * Ativa quando "pagamento.gateway.provider=fake"
 * Basta trocar esse valor e fornecer o bean equivalente quando o gateway real
 * estiver pronto para uso.
 */
@Service
public class FakePaymentGateway implements PaymentGateway {

    @Value("${pagamento.gateway.provider:fake}")
    private String provider;

    @Override
    public RespostaCobranca criarCobranca(SolicitacaoCobranca solicitacao) {
        String idFake = "FAKE-" + UUID.randomUUID();
        String linkFake = "https://pagamentos.alugafacil.local/checkout/" + idFake;
        return new RespostaCobranca(idFake, "approved", linkFake);
    }

    @Override
    public void estornarCobranca(String idTransacaoGateway) {
        // Sem efeito real: apenas registra a intencao de estorno.
        // Na implementacao real, aqui vai a chamada de estorno da API do provedor.
    }
}
