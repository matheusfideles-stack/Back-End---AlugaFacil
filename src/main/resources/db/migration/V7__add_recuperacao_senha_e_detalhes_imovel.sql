CREATE TABLE tokens_recuperacao_senha (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          usuario_id BIGINT NOT NULL,
                                          token_hash VARCHAR(64) NOT NULL UNIQUE,
                                          expira_em TIMESTAMP NOT NULL,
                                          usado_em TIMESTAMP NULL,
                                          criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

ALTER TABLE usuarios ADD COLUMN email_confirmado BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE tokens_confirmacao_email (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          usuario_id BIGINT NOT NULL,
                                          token_hash VARCHAR(64) NOT NULL UNIQUE,
                                          expira_em TIMESTAMP NOT NULL,
                                          usado_em TIMESTAMP NULL,
                                          criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

ALTER TABLE imoveis ADD COLUMN quartos INT;
ALTER TABLE imoveis ADD COLUMN banheiros INT;
ALTER TABLE imoveis ADD COLUMN vagas INT;
ALTER TABLE imoveis ADD COLUMN area_m2 INT;

CREATE TABLE manutencoes (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             contrato_id BIGINT NOT NULL,
                             tipo VARCHAR(50) NOT NULL,
                             urgencia VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                             descricao VARCHAR(1000) NOT NULL,
                             status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
                             criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (contrato_id) REFERENCES contratos(id)
);
