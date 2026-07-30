-- V011 — contacts (database.md §7.12, entities.md §6.5).
--
-- Pessoas de referência dentro de um cliente.

CREATE TABLE contacts (
    id               UUID         NOT NULL,
    tenant_id        UUID         NOT NULL,
    client_id        UUID         NOT NULL,
    name             VARCHAR(150) NOT NULL,
    email            VARCHAR(255) NULL,
    phone            VARCHAR(20)  NULL,
    contact_role     VARCHAR(80)  NULL,
    is_primary       BOOLEAN      NOT NULL DEFAULT false,
    receives_reports BOOLEAN      NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID         NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       UUID         NULL,
    deleted_at       TIMESTAMPTZ  NULL,
    deleted_by       UUID         NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_contacts PRIMARY KEY (id),
    CONSTRAINT fk_contacts_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_contacts_clients FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT ck_contacts_name_length CHECK (length(btrim(name)) >= 2)
);

-- INV-CON-01 / RN-406: no máximo um contato principal por cliente. O índice é a segunda barreira;
-- a desmarcação automática do anterior ocorre em PrimaryContactPolicy, na mesma transação (CX-08).
CREATE UNIQUE INDEX uq_contacts_primary
    ON contacts (client_id)
    WHERE deleted_at IS NULL AND is_primary = true;

CREATE INDEX idx_contacts_client
    ON contacts (tenant_id, client_id) WHERE deleted_at IS NULL;
