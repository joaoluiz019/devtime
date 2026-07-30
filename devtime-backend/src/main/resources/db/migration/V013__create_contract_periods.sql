-- V013 — contract_periods (database.md §7.6, entities.md §6.7).
--
-- Ciclo de apuração: a unidade sobre a qual o banco de horas existe.
--
-- A constraint EXCLUDE de INV-PER-02 (prevista como V014 em specs/004/tasks.md) é criada aqui, na
-- mesma migration da tabela que ela protege: V014 e V015 estão reservadas a tickets e timers em
-- database.md §8.1, que prevalece pela hierarquia IA-11. Nenhuma linha existe entre a criação da
-- tabela e a da constraint, então não há janela de inconsistência.

CREATE TABLE contract_periods (
    id                    UUID           NOT NULL,
    tenant_id             UUID           NOT NULL,
    contract_id           UUID           NOT NULL,
    sequence              INTEGER        NOT NULL,
    label                 VARCHAR(30)    NOT NULL,
    start_date            DATE           NOT NULL,
    -- INCLUSIVE (entities.md §7.2): todo intervalo de datas no DevTime é [start, end].
    end_date              DATE           NOT NULL,
    status                VARCHAR(15)    NOT NULL DEFAULT 'SCHEDULED',
    -- Congelado na criação (🔒): alterar monthlyMinutes não reescreve períodos existentes (RN-207).
    contracted_minutes    INTEGER        NOT NULL DEFAULT 0,
    carried_in_minutes    INTEGER        NOT NULL DEFAULT 0,
    carried_out_minutes   INTEGER        NOT NULL DEFAULT 0,
    adjustment_minutes    INTEGER        NOT NULL DEFAULT 0,
    consumed_minutes      INTEGER        NOT NULL DEFAULT 0,
    non_billable_minutes  INTEGER        NOT NULL DEFAULT 0,
    closed_at             TIMESTAMPTZ    NULL,
    closed_by             UUID           NULL,
    reopened_at           TIMESTAMPTZ    NULL,
    reopen_count          SMALLINT       NOT NULL DEFAULT 0,
    hourly_rate_snapshot  NUMERIC(19, 4) NULL,
    overage_rate_snapshot NUMERIC(19, 4) NULL,
    currency              CHAR(3)        NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID           NULL,
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by            UUID           NULL,
    deleted_at            TIMESTAMPTZ    NULL,
    deleted_by            UUID           NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_contract_periods PRIMARY KEY (id),
    CONSTRAINT fk_periods_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_periods_contracts FOREIGN KEY (contract_id) REFERENCES contracts (id),
    CONSTRAINT fk_periods_users_closed_by FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT ck_periods_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_periods_status CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSING', 'CLOSED', 'REOPENED')),
    -- INV-PER-04.
    CONSTRAINT ck_periods_date_range CHECK (end_date >= start_date),
    -- INV-PER-05.
    CONSTRAINT ck_periods_contracted_minutes CHECK (contracted_minutes >= 0),
    CONSTRAINT ck_periods_carried_in CHECK (carried_in_minutes >= 0),
    CONSTRAINT ck_periods_carried_out CHECK (carried_out_minutes >= 0),
    CONSTRAINT ck_periods_consumed CHECK (consumed_minutes >= 0),
    CONSTRAINT ck_periods_non_billable CHECK (non_billable_minutes >= 0),
    CONSTRAINT ck_periods_reopen_count CHECK (reopen_count >= 0),
    -- INV-PER-06: CLOSED exige closedAt e closedBy.
    CONSTRAINT ck_periods_closed_requires_actor CHECK (status <> 'CLOSED' OR (closed_at IS NOT NULL AND closed_by IS NOT NULL))
);

-- INV-PER-01.
CREATE UNIQUE INDEX uq_periods_contract_sequence
    ON contract_periods (contract_id, sequence) WHERE deleted_at IS NULL;

-- INV-PER-02: períodos do mesmo contrato nunca se sobrepõem.
-- Aqui a constraint EXCLUDE é o mecanismo primário — diferentemente de work_logs (§5.4) — porque
-- períodos são gerados exclusivamente pelo sistema, não têm mensagem de erro voltada ao usuário e
-- a violação representa corrupção estrutural, que deve ser impedida no nível mais baixo possível.
ALTER TABLE contract_periods ADD CONSTRAINT ex_periods_no_overlap
    EXCLUDE USING gist (
        contract_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (deleted_at IS NULL);

-- INV-PER-07: no máximo um período OPEN por contrato.
CREATE UNIQUE INDEX uq_periods_single_open
    ON contract_periods (contract_id)
    WHERE deleted_at IS NULL AND status = 'OPEN';

-- RN-107: resolução do período que contém uma data de trabalho.
CREATE INDEX idx_periods_contract_dates
    ON contract_periods (tenant_id, contract_id, start_date, end_date) WHERE deleted_at IS NULL;

-- Sustenta GeneratePeriodsJob e OpenScheduledPeriodsJob (introduzidos em S4).
CREATE INDEX idx_periods_status_end
    ON contract_periods (tenant_id, status, end_date) WHERE deleted_at IS NULL;
