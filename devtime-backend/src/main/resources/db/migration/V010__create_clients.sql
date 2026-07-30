-- V010 — clients (database.md §7.4, entities.md §6.4).
--
-- Contraparte contratante. O Value Object Address é achatado em address_* (entities.md §7.1),
-- reaproveitando exatamente os nomes de coluna já usados em tenants (V002).

CREATE TABLE clients (
    id                     UUID         NOT NULL,
    tenant_id              UUID         NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    legal_name             VARCHAR(200) NULL,
    document_type          VARCHAR(10)  NULL,
    -- Apenas dígitos: a máscara é removida na normalização (CX-03).
    document_number        VARCHAR(20)  NULL,
    email                  VARCHAR(255) NULL,
    phone                  VARCHAR(20)  NULL,
    website                VARCHAR(255) NULL,
    address_street         VARCHAR(200) NULL,
    address_number         VARCHAR(20)  NULL,
    address_complement     VARCHAR(100) NULL,
    address_district       VARCHAR(100) NULL,
    address_city           VARCHAR(100) NULL,
    address_state          VARCHAR(50)  NULL,
    address_zip_code       VARCHAR(20)  NULL,
    address_country        CHAR(2)      NULL,
    notes                  TEXT         NULL,
    color                  CHAR(7)      NULL,
    status                 VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    -- Campo desnormalizado (entities.md §9): contratos em ACTIVE ou SUSPENDED, mantido pela
    -- feature 004 nas transições. Sustenta RN-401 sem agregação a cada exclusão.
    active_contracts_count INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             UUID         NULL,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             UUID         NULL,
    deleted_at             TIMESTAMPTZ  NULL,
    deleted_by             UUID         NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_clients PRIMARY KEY (id),
    CONSTRAINT fk_clients_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_clients_name_length CHECK (length(btrim(name)) >= 2),
    CONSTRAINT ck_clients_document_type CHECK (document_type IS NULL OR document_type IN ('CPF', 'CNPJ', 'OTHER')),
    CONSTRAINT ck_clients_document_digits CHECK (document_number IS NULL OR document_number ~ '^[0-9]+$'),
    CONSTRAINT ck_clients_notes_length CHECK (notes IS NULL OR length(notes) <= 4000),
    CONSTRAINT ck_clients_color_format CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_clients_active_contracts_count CHECK (active_contracts_count >= 0)
);

-- INV-CLI-01 / RN-403: único quando informado. Parcial em dois eixos (ART-055): ignora excluídos
-- (CE-C-03) e clientes sem documento (CE-C-01).
CREATE UNIQUE INDEX uq_clients_tenant_document
    ON clients (tenant_id, document_number)
    WHERE deleted_at IS NULL AND document_number IS NOT NULL;

-- INV-CLI-02 / RN-404: unicidade de nome sem diferenciar caixa, mas COM acentos (CX-12, CE-C-02).
CREATE UNIQUE INDEX uq_clients_tenant_name
    ON clients (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE INDEX idx_clients_tenant_status
    ON clients (tenant_id, status) WHERE deleted_at IS NULL;

-- T-003-03: busca por texto sem acento e sem caixa. A expressão indexada é a mesma aplicada na
-- consulta (unaccent não está disponível sem a extensão; a normalização ocorre na aplicação e o
-- índice trigram acelera o LIKE resultante).
CREATE INDEX idx_clients_tenant_search
    ON clients USING gin (name gin_trgm_ops);
