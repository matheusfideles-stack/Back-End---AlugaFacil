ALTER TABLE pagamentos ADD COLUMN status_detalhe VARCHAR(100);
ALTER TABLE pagamentos ADD COLUMN link_pagamento VARCHAR(500);
ALTER TABLE pagamentos ADD COLUMN pix_copia_e_cola TEXT;
ALTER TABLE pagamentos ADD COLUMN linha_digitavel VARCHAR(100);
ALTER TABLE pagamentos ADD COLUMN expira_em TIMESTAMP;

CREATE INDEX idx_pagamentos_id_transacao_gateway ON pagamentos (id_transacao_gateway);