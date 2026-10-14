CREATE TABLE imoveis (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         locador_id BIGINT NOT NULL,
                         titulo VARCHAR(150) NOT NULL,
                         descricao VARCHAR(1000),
                         tipo VARCHAR(20) NOT NULL,
                         logradouro VARCHAR(150) NOT NULL,
                         numero VARCHAR(10) NOT NULL,
                         complemento VARCHAR(100),
                         bairro VARCHAR(100) NOT NULL,
                         cidade VARCHAR(100) NOT NULL,
                         uf CHAR(2) NOT NULL,
                         cep VARCHAR(9) NOT NULL,
                         valor_aluguel NUMERIC(10,2) NOT NULL,
                         valor_condominio NUMERIC(10,2) DEFAULT 0,
                         disponivel BOOLEAN NOT NULL DEFAULT TRUE,
                         criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (locador_id) REFERENCES usuarios(id)
);