-- V007 — shedlock (database.md §7.12 e §8.1, fase F0).
--
-- Tabela de infraestrutura do ShedLock (JB-01). Não é entidade de domínio: não possui
-- tenant_id, campos de auditoria nem version, e é gerenciada pela biblioteca.

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
