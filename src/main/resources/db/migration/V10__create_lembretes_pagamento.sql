CREATE TABLE lembretes_pagamento (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     contrato_id BIGINT NOT NULL,
                                     referencia_mes VARCHAR(7) NOT NULL,
                                     estagio VARCHAR(20) NOT NULL,
                                     enviado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     CONSTRAINT uk_lembrete_contrato_mes_estagio UNIQUE (contrato_id, referencia_mes, estagio),
                                     FOREIGN KEY (contrato_id) REFERENCES contratos(id)
);
