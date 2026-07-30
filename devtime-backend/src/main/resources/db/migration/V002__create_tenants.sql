-- V002 — tenants (database.md §7.1, entities.md §6.1).
--
-- Raiz de isolamento. Não possui tenant_id (exceção declarada em database.md §4.3).

CREATE TABLE tenants (
    id                UUID         NOT NULL,
    name              VARCHAR(120) NOT NULL,
    slug              VARCHAR(60)  NOT NULL,
    legal_name        VARCHAR(200) NULL,
    document_number   VARCHAR(20)  NULL,
    email             VARCHAR(255) NOT NULL,
    phone             VARCHAR(20)  NULL,
    timezone          VARCHAR(60)  NOT NULL DEFAULT 'America/Sao_Paulo',
    locale            VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    currency          CHAR(3)      NOT NULL DEFAULT 'BRL',
    logo_url          VARCHAR(500) NULL,
    -- Value Object Address achatado (entities.md §7.1).
    address_street    VARCHAR(200) NULL,
    address_number    VARCHAR(20)  NULL,
    address_complement VARCHAR(100) NULL,
    address_district  VARCHAR(100) NULL,
    address_city      VARCHAR(100) NULL,
    address_state     VARCHAR(50)  NULL,
    address_zip_code  VARCHAR(20)  NULL,
    address_country   CHAR(2)      NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    plan_code         VARCHAR(30)  NOT NULL DEFAULT 'FREE',
    settings          JSONB        NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID         NULL,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        UUID         NULL,
    deleted_at        TIMESTAMPTZ  NULL,
    deleted_by        UUID         NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT ck_tenants_name_length CHECK (length(name) >= 2),
    CONSTRAINT ck_tenants_slug_format CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,58}[a-z0-9])?$'),
    CONSTRAINT ck_tenants_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED'))
);

-- INV-TEN-01: slug único globalmente entre tenants não excluídos.
-- ART-055: índice parcial, para que a exclusão lógica não bloqueie a reutilização do slug.
CREATE UNIQUE INDEX uq_tenants_slug ON tenants (slug) WHERE deleted_at IS NULL;

CREATE INDEX idx_tenants_status ON tenants (status) WHERE deleted_at IS NULL;
