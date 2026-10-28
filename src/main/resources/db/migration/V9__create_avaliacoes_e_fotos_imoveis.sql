CREATE TABLE fotos_imovel (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              imovel_id BIGINT NOT NULL,
                              arquivo VARCHAR(80) NOT NULL,
                              content_type VARCHAR(30) NOT NULL,
                              tamanho BIGINT NOT NULL,
                              ordem INT NOT NULL,
                              criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (imovel_id) REFERENCES imoveis(id)
);

CREATE TABLE avaliacoes (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            imovel_id BIGINT NOT NULL,
                            usuario_id BIGINT NOT NULL,
                            nota INT NOT NULL,
                            comentario VARCHAR(1000),
                            criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT uk_avaliacao_imovel_usuario UNIQUE (imovel_id, usuario_id),
                            FOREIGN KEY (imovel_id) REFERENCES imoveis(id),
                            FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
