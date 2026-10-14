CREATE TABLE pagamentos (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            contrato_id BIGINT NOT NULL,
                            referencia_mes VARCHAR(7) NOT NULL,
                            valor NUMERIC(10,2) NOT NULL,
                            metodo VARCHAR(20) NOT NULL,
                            status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
                            id_transacao_gateway VARCHAR(100),
                            vencimento DATE NOT NULL,
                            pago_em TIMESTAMP,
                            criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE (contrato_id, referencia_mes),
                            FOREIGN KEY (contrato_id) REFERENCES contratos(id)
);