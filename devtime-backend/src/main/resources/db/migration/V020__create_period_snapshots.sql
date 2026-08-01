-- V020 — period_snapshots (database.md §8.1, entities.md §6.9).
--
-- Âncora da imutabilidade dos relatórios (ART-005): depois que um período fecha e o relatório vai
-- ao cliente, aquele número não muda — nem por edição de work log, nem por alteração de contrato,
-- nem por recálculo. RN-701 serve relatórios de período fechado exclusivamente daqui.
--
-- Numeração conforme database.md §8.1 (V020 = period_snapshots), que prevalece sobre
-- specs/011-bank-hours/tasks.md (V030).

CREATE TABLE period_snapshots (
    id                 UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    contract_period_id UUID        NOT NULL,
    -- 🔒 instante do fechamento que o gerou.
    snapshot_at        TIMESTAMPTZ NOT NULL,
    -- 🔒 cópia integral do relatório: tenant, cliente, contrato, período, totais, work logs e
    -- ajustes (entities.md §6.9).
    payload            JSONB       NOT NULL,
    -- 🔒 SHA-256 do payload canonicalizado. SG-05: adulteração direta no banco é detectável.
    checksum           CHAR(64)    NOT NULL,
    schema_version     INTEGER     NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID        NULL,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         UUID        NULL,
    deleted_at         TIMESTAMPTZ NULL,
    deleted_by         UUID        NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_period_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_snapshots_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_snapshots_periods FOREIGN KEY (contract_period_id) REFERENCES contract_periods (id),
    CONSTRAINT ck_snapshots_schema_version CHECK (schema_version >= 1)
);

-- INV-SNP-01 / CX-18: a unicidade é (contract_period_id, snapshot_at) e NÃO apenas
-- contract_period_id. Um período reaberto e refechado gera um SEGUNDO snapshot; a unicidade
-- simples impediria o refechamento. Ambos ficam preservados, versionados por snapshot_at.
CREATE UNIQUE INDEX uq_snapshots_period_at
    ON period_snapshots (contract_period_id, snapshot_at);

-- Leitura do snapshot mais recente do período.
CREATE INDEX idx_snapshots_period
    ON period_snapshots (tenant_id, contract_period_id, snapshot_at DESC);
