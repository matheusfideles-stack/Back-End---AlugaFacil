CREATE TABLE contratos (
    id BIGSERIAL PRIMARY KEY,
    imovel_id BIGINT NOT NULL REFERENCES imoveis(id),
    locatario_id BIGINT NOT NULL REFERENCES usuarios(id),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    dia_vencimento INTEGER NOT NULL,
    valor_mensal NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);
