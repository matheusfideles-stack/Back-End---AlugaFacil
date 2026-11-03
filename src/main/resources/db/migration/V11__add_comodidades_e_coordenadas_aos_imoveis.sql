ALTER TABLE imoveis ADD COLUMN latitude DOUBLE;
ALTER TABLE imoveis ADD COLUMN longitude DOUBLE;

CREATE TABLE imovel_comodidades (
                                    imovel_id BIGINT NOT NULL,
                                    comodidade VARCHAR(40) NOT NULL,
                                    PRIMARY KEY (imovel_id, comodidade),
                                    FOREIGN KEY (imovel_id) REFERENCES imoveis(id)
);
