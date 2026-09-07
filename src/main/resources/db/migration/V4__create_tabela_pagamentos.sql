CREATE TABLE pagamentos (
    id BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT NOT NULL REFERENCES contratos(id),
    referencia_mes VARCHAR(7) NOT NULL, -- formato AAAA-MM
    valor NUMERIC(10,2) NOT NULL,
    metodo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    id_transacao_gateway VARCHAR(100),
    vencimento DATE NOT NULL,
    pago_em TIMESTAMP,
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (contrato_id, referencia_mes)
);
