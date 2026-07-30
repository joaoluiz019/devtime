-- V008 — categories (database.md §7.12, entities.md §6.10).
--
-- Catálogo de classificação do trabalho. A numeração segue database.md §8.1 (V008 = categories),
-- que prevalece sobre specs/005-categories/tasks.md T-005-01 (V016) pela hierarquia IA-11:
-- 03-architecture/ precede specs/. V016 está reservada a work_logs.

CREATE TABLE categories (
    id                  UUID         NOT NULL,
    tenant_id           UUID         NOT NULL,
    name                VARCHAR(60)  NOT NULL,
    description         VARCHAR(255) NULL,
    color               CHAR(7)      NOT NULL DEFAULT '#6366F1',
    icon                VARCHAR(40)  NULL,
    billable_by_default BOOLEAN      NOT NULL DEFAULT true,
    active              BOOLEAN      NOT NULL DEFAULT true,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    -- INV-CAT-05: imutável após a criação; ausente dos DTOs de atualização.
    is_system           BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID         NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          UUID         NULL,
    deleted_at          TIMESTAMPTZ  NULL,
    deleted_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_categories_name_length CHECK (length(btrim(name)) >= 2),
    -- §8.2 de users.md: cor fora do formato hexadecimal resulta em DEVTIME-2000.
    CONSTRAINT ck_categories_color_format CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_categories_sort_order CHECK (sort_order >= 0)
);

-- INV-CAT-01 / RN-502: unicidade por tenant sem diferenciar caixa.
-- Parcial (ART-055): permite recadastrar um nome cujo registro anterior foi excluído (CX-03).
CREATE UNIQUE INDEX uq_categories_tenant_name
    ON categories (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- Sustenta a listagem ordenada e a cadeia de resolução da §6.2 da spec (T-005-02).
CREATE INDEX idx_categories_tenant_active_order
    ON categories (tenant_id, active, sort_order, name) WHERE deleted_at IS NULL;
