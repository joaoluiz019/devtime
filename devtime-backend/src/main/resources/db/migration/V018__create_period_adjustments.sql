-- V018 — period_adjustments (database.md §8.1, entities.md §6.8).
--
-- Ajuste manual e auditável do saldo de um período. Numeração conforme database.md §8.1
-- (V018 = period_adjustments), que prevalece sobre specs/011-bank-hours/tasks.md (V029).
--
-- INV-ADJ-01: ajustes são IMUTÁVEIS. Correção se faz por um novo ajuste de sinal contrário
-- (estorno), nunca por edição — e não existe rota de PATCH nem de DELETE (RN-236). A ausência de
-- rota é parte da garantia: o que não tem caminho não é feito por engano.
--
-- A tabela não declara deleted_at/deleted_by com uso: as colunas existem por BaseEntity (ART-050)
-- e permanecem nulas. Excluir um ajuste apagaria a explicação de um saldo que o cliente já viu.

CREATE TABLE period_adjustments (
    id                 UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    contract_period_id UUID        NOT NULL,
    -- 🔒 positivo credita, negativo debita. Zero é ajuste sem efeito — sempre erro de digitação.
    minutes            INTEGER     NOT NULL,
    reason             VARCHAR(20) NOT NULL,
    justification      TEXT        NOT NULL,
    -- 🔒 sempre o usuário autenticado; ausente do DTO de escrita.
    applied_by         UUID        NOT NULL,
    applied_at         TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID        NULL,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         UUID        NULL,
    deleted_at         TIMESTAMPTZ NULL,
    deleted_by         UUID        NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_period_adjustments PRIMARY KEY (id),
    CONSTRAINT fk_adjustments_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_adjustments_periods FOREIGN KEY (contract_period_id) REFERENCES contract_periods (id),
    CONSTRAINT fk_adjustments_users FOREIGN KEY (applied_by) REFERENCES users (id),
    CONSTRAINT ck_adjustments_minutes CHECK (minutes <> 0),
    -- RN-215: justificativa com no mínimo 10 caracteres. Um ajuste sem motivo registrado é
    -- indefensável em disputa contratual.
    CONSTRAINT ck_adjustments_justification CHECK (length(btrim(justification)) BETWEEN 10 AND 1000),
    CONSTRAINT ck_adjustments_reason CHECK (reason IN ('COURTESY', 'CORRECTION', 'NEGOTIATED_EXTRA', 'PENALTY', 'MIGRATION', 'OTHER'))
);

-- Extrato de ajustes do período, em ordem cronológica (§10 de contracts.md).
CREATE INDEX idx_adjustments_period
    ON period_adjustments (tenant_id, contract_period_id, applied_at);

-- RN-230: RolloverExpiryJob localiza períodos com saldo transportado a expirar.
CREATE INDEX idx_periods_rollover_expiry
    ON contract_periods (tenant_id, contract_id, sequence) WHERE carried_in_minutes > 0;

-- CE-ME-07: StuckClosingJob detecta períodos presos em CLOSING há mais de 10 minutos.
CREATE INDEX idx_periods_closing_stuck
    ON contract_periods (status, updated_at) WHERE status = 'CLOSING';
