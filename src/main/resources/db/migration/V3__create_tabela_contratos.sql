CREATE TABLE contratos (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           imovel_id BIGINT NOT NULL,
                           locatario_id BIGINT NOT NULL,
                           data_inicio DATE NOT NULL,
                           data_fim DATE NOT NULL,
                           dia_vencimento INTEGER NOT NULL,
                           valor_mensal NUMERIC(10,2) NOT NULL,
                           status VARCHAR(20) NOT NULL,
                           criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           FOREIGN KEY (imovel_id) REFERENCES imoveis(id),
                           FOREIGN KEY (locatario_id) REFERENCES usuarios(id)
);