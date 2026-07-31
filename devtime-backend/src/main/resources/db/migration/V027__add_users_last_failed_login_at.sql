-- V027 — users.last_failed_login_at (RN-453, T-001-23).
--
-- RN-453 exige "5 tentativas falhas **em 15 minutos**". A janela não é implementável apenas com
-- `failed_login_attempts`: sem saber quando a última falha ocorreu, o contador nunca reiniciaria e
-- cinco erros de digitação espalhados por meses bloqueariam a conta — comportamento que a regra
-- explicitamente não pede.
--
-- entities.md §6.2 não previa a coluna; a lacuna foi reportada e o documento atualizado junto com
-- esta migration. Aditiva e anulável (MG-03): não exige DEFAULT nem backfill, e a aplicação
-- anterior continua operando sem enxergá-la (MG-02).

ALTER TABLE users ADD COLUMN last_failed_login_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN users.last_failed_login_at IS
    'RN-453: início da janela de 15 minutos de contagem de falhas de login.';
