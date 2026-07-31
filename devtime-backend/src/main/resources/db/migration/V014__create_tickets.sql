-- V014 — tickets (database.md §7.7, entities.md §6.12).
--
-- Unidade de trabalho à qual todo work log pertence (RN-101). Numeração conforme database.md §8.1
-- (V014 = tickets), que prevalece sobre specs/007-tickets/tasks.md T-007-01 a T-007-03
-- (V019–V021) pela hierarquia IA-11.
--
-- NÃO existe coluna `key`. entities.md §6.12 marca `key` como 📐 (campo derivado, não persistido) e
-- database.md §7.7 não a declara; ambos precedem specs/007 §13.2, que a descreve como persistida.
-- A chave é remontada na leitura como {contract.code}-{number} e a busca por chave é resolvida
-- decompondo-a em (código do contrato, número), servida por uq_tickets_contract_number.
--
-- Também não existe objeto de sequência por contrato: database.md §7.7 fixa a obtenção do `number`
-- por max(number)+1 dentro da transação de criação, e registra a alternativa rejeitada (uma
-- SEQUENCE por contrato seria um objeto de schema por linha de `contracts`). A atomicidade vem do
-- lock consultivo por contrato em TicketNumberGenerator, com este índice único como barreira final.

CREATE TABLE tickets (
    id                  UUID         NOT NULL,
    tenant_id           UUID         NOT NULL,
    contract_id         UUID         NOT NULL,
    number              INTEGER      NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NULL,
    type                VARCHAR(20)  NOT NULL DEFAULT 'FEATURE',
    status              VARCHAR(20)  NOT NULL DEFAULT 'BACKLOG',
    priority            VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    assignee_id         UUID         NULL,
    reporter_id         UUID         NOT NULL,
    estimated_minutes   INTEGER      NULL,
    -- 💾 RN-308: atualizado por incremento na transação do work log, nunca por reagregação.
    spent_minutes       INTEGER      NOT NULL DEFAULT 0,
    billable_minutes    INTEGER      NOT NULL DEFAULT 0,
    block_reason        VARCHAR(500) NULL,
    due_date            DATE         NULL,
    started_at          TIMESTAMPTZ  NULL,
    completed_at        TIMESTAMPTZ  NULL,
    external_ref        VARCHAR(200) NULL,
    default_category_id UUID         NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID         NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          UUID         NULL,
    deleted_at          TIMESTAMPTZ  NULL,
    deleted_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT fk_tickets_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tickets_contracts FOREIGN KEY (contract_id) REFERENCES contracts (id),
    -- database.md §7.7: assignee_id e reporter_id referenciam `users`. A validação de membership
    -- ACTIVE (RN-304) é da aplicação — `memberships.user_id` não é único isoladamente, porque um
    -- usuário participa de vários tenants, e portanto não pode ser alvo de FK.
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_tickets_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_tickets_categories FOREIGN KEY (default_category_id) REFERENCES categories (id),
    -- RN-302: a sequência começa em 1.
    CONSTRAINT ck_tickets_number CHECK (number >= 1),
    -- RN-303.
    CONSTRAINT ck_tickets_title_length CHECK (length(btrim(title)) BETWEEN 3 AND 200),
    CONSTRAINT ck_tickets_description_length CHECK (description IS NULL OR length(description) <= 20000),
    CONSTRAINT ck_tickets_type CHECK (type IN ('FEATURE', 'BUG', 'SUPPORT', 'MEETING', 'MAINTENANCE', 'OTHER')),
    CONSTRAINT ck_tickets_status CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_tickets_estimated_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    -- INV-TCK-05: spentMinutes >= billableMinutes >= 0.
    CONSTRAINT ck_tickets_spent_minutes CHECK (spent_minutes >= 0),
    CONSTRAINT ck_tickets_billable_minutes CHECK (billable_minutes >= 0 AND billable_minutes <= spent_minutes),
    -- INV-TCK-04: DONE exige completedAt preenchido.
    CONSTRAINT ck_tickets_done_completed_at CHECK (status <> 'DONE' OR completed_at IS NOT NULL),
    -- state-machines.md §4.7: BLOCKED exige motivo com no mínimo 5 caracteres.
    CONSTRAINT ck_tickets_block_reason CHECK (status <> 'BLOCKED' OR length(btrim(coalesce(block_reason, ''))) >= 5)
);

-- INV-TCK-01 / RN-302: numeração sequencial por contrato.
CREATE UNIQUE INDEX uq_tickets_contract_number
    ON tickets (contract_id, number) WHERE deleted_at IS NULL;

CREATE INDEX idx_tickets_tenant_status_priority
    ON tickets (tenant_id, status, priority) WHERE deleted_at IS NULL;

-- Listagem e quadro ordenados por atualização (spec 007 §13.4).
CREATE INDEX idx_tickets_tenant_status_updated
    ON tickets (tenant_id, status, updated_at DESC) WHERE deleted_at IS NULL;

-- "Meus tickets" e escopo de dados de MEMBER.
CREATE INDEX idx_tickets_tenant_assignee
    ON tickets (tenant_id, assignee_id, status) WHERE deleted_at IS NULL;

CREATE INDEX idx_tickets_tenant_contract
    ON tickets (tenant_id, contract_id, status) WHERE deleted_at IS NULL;

-- Prazos (spec 007 §13.4).
CREATE INDEX idx_tickets_tenant_due
    ON tickets (tenant_id, due_date) WHERE due_date IS NOT NULL AND deleted_at IS NULL;

-- Busca textual. `unaccent` não é usado por não constar das extensões instaladas em V001; a
-- configuração 'portuguese' já normaliza caixa e aplica stemming do idioma.
CREATE INDEX idx_tickets_search
    ON tickets USING gin (to_tsvector('portuguese', title || ' ' || coalesce(description, '')));
