ALTER TABLE tokens_recuperacao_senha ADD COLUMN tentativas INT NOT NULL DEFAULT 0;
ALTER TABLE tokens_confirmacao_email ADD COLUMN tentativas INT NOT NULL DEFAULT 0;
