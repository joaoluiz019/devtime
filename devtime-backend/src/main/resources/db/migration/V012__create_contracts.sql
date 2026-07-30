-- V012 — contracts (database.md §7.5, entities.md §6.6).
--
-- Objeto central do produto: define quantas horas existem e em qual janela.
--
-- Sobre a sequência de código CT-XXXX (T-004-04, prevista como V015): não é criada uma tabela nem
-- uma SEQUENCE. Uma SEQUENCE do PostgreSQL é global e não se particiona por tenant, e uma tabela de
-- contadores acrescentaria schema não previsto em database.md §7. O código é derivado do maior
-- código já usado no tenant, com uq_contracts_tenant_code como barreira contra corrida —
-- alternativa avaliada e registrada em ContractCodeGenerator.

CREATE TABLE contracts (
    id                       UUID           NOT NULL,
    tenant_id                UUID           NOT NULL,
    client_id                UUID           NOT NULL,
    code                     VARCHAR(30)    NOT NULL,
    name                     VARCHAR(150)   NOT NULL,
    description              TEXT           NULL,
    type                     VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY_HOURS',
    status                   VARCHAR(15)    NOT NULL DEFAULT 'DRAFT',
    monthly_minutes          INTEGER        NULL,
    start_date               DATE           NOT NULL,
    end_date                 DATE           NULL,
    billing_day              SMALLINT       NOT NULL,
    rollover_policy          VARCHAR(10)    NOT NULL DEFAULT 'NONE',
    rollover_cap_minutes     INTEGER        NULL,
    rollover_expiry_periods  SMALLINT       NOT NULL DEFAULT 1,
    overage_policy           VARCHAR(20)    NOT NULL DEFAULT 'WARN',
    hourly_rate              NUMERIC(19, 4) NULL,
    overage_rate             NUMERIC(19, 4) NULL,
    currency                 CHAR(3)        NOT NULL,
    auto_renew               BOOLEAN        NOT NULL DEFAULT true,
    prorate_first_period     BOOLEAN        NOT NULL DEFAULT true,
    notification_thresholds  SMALLINT[]     NOT NULL DEFAULT '{50,80,100}',
    default_category_id      UUID           NULL,
    notes                    TEXT           NULL,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by               UUID           NULL,
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by               UUID           NULL,
    deleted_at               TIMESTAMPTZ    NULL,
    deleted_by               UUID           NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_contracts PRIMARY KEY (id),
    CONSTRAINT fk_contracts_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_contracts_clients FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_contracts_categories FOREIGN KEY (default_category_id) REFERENCES categories (id),
    CONSTRAINT ck_contracts_type CHECK (type IN ('MONTHLY_HOURS', 'HOURLY_OPEN')),
    CONSTRAINT ck_contracts_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'ENDED', 'CANCELLED')),
    -- RN-202: 44.640 minutos = 31 dias. Valor fora disso é erro de digitação.
    CONSTRAINT ck_contracts_monthly_minutes CHECK (monthly_minutes IS NULL OR monthly_minutes BETWEEN 1 AND 44640),
    -- RN-203: dias 29–31 não existem em todos os meses; restringir a 28 elimina a ambiguidade.
    CONSTRAINT ck_contracts_billing_day CHECK (billing_day BETWEEN 1 AND 28),
    -- RN-204 / INV-CTR-05.
    CONSTRAINT ck_contracts_date_range CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_contracts_rollover_policy CHECK (rollover_policy IN ('NONE', 'FULL', 'CAPPED')),
    CONSTRAINT ck_contracts_overage_policy CHECK (overage_policy IN ('BLOCK', 'WARN', 'ALLOW_BILLABLE')),
    CONSTRAINT ck_contracts_rollover_cap CHECK (rollover_cap_minutes IS NULL OR rollover_cap_minutes >= 0),
    CONSTRAINT ck_contracts_rollover_expiry CHECK (rollover_expiry_periods >= 0),
    CONSTRAINT ck_contracts_hourly_rate CHECK (hourly_rate IS NULL OR hourly_rate >= 0),
    CONSTRAINT ck_contracts_overage_rate CHECK (overage_rate IS NULL OR overage_rate >= 0),
    CONSTRAINT ck_contracts_description_length CHECK (description IS NULL OR length(description) <= 4000),
    -- INV-CTR-02: MONTHLY_HOURS exige monthlyMinutes.
    CONSTRAINT ck_contracts_monthly_requires_minutes CHECK (type <> 'MONTHLY_HOURS' OR monthly_minutes IS NOT NULL),
    -- INV-CTR-03: HOURLY_OPEN não tem saldo nem rollover.
    CONSTRAINT ck_contracts_open_no_minutes CHECK (type <> 'HOURLY_OPEN' OR (monthly_minutes IS NULL AND rollover_policy = 'NONE')),
    -- INV-CTR-04: CAPPED exige teto de transporte.
    CONSTRAINT ck_contracts_capped_requires_cap CHECK (rollover_policy <> 'CAPPED' OR rollover_cap_minutes IS NOT NULL)
);

-- INV-CTR-01: código único por tenant entre contratos não excluídos.
CREATE UNIQUE INDEX uq_contracts_tenant_code
    ON contracts (tenant_id, code) WHERE deleted_at IS NULL;

CREATE INDEX idx_contracts_tenant_client_status
    ON contracts (tenant_id, client_id, status) WHERE deleted_at IS NULL;

CREATE INDEX idx_contracts_tenant_status_end
    ON contracts (tenant_id, status, end_date) WHERE deleted_at IS NULL;
