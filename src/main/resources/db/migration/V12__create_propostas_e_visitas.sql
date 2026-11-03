CREATE TABLE propostas (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           imovel_id BIGINT NOT NULL,
                           locatario_id BIGINT NOT NULL,
                           data_inicio DATE NOT NULL,
                           data_fim DATE NOT NULL,
                           dia_vencimento INT NOT NULL,
                           mensagem VARCHAR(500),
                           status VARCHAR(20) NOT NULL,
                           motivo_recusa VARCHAR(300),
                           contrato_id BIGINT NULL,
                           criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           respondido_em TIMESTAMP NULL,
                           FOREIGN KEY (imovel_id) REFERENCES imoveis(id),
                           FOREIGN KEY (locatario_id) REFERENCES usuarios(id),
                           FOREIGN KEY (contrato_id) REFERENCES contratos(id)
);

CREATE TABLE visitas (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         imovel_id BIGINT NOT NULL,
                         locatario_id BIGINT NOT NULL,
                         data_hora TIMESTAMP NOT NULL,
                         mensagem VARCHAR(500),
                         status VARCHAR(20) NOT NULL,
                         criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (imovel_id) REFERENCES imoveis(id),
                         FOREIGN KEY (locatario_id) REFERENCES usuarios(id)
);
