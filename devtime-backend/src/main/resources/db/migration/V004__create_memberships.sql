-- V004 — memberships (database.md §7.3, entities.md §6.3).
--
-- Vincula User a Tenant com um papel. Primeira tabela tenant-scoped do schema (ART-013).

CREATE TABLE memberships (
    id                  UUID           NOT NULL,
    tenant_id           UUID           NOT NULL,
    user_id             UUID           NOT NULL,
    role                VARCHAR(20)    NOT NULL DEFAULT 'MEMBER',
    status              VARCHAR(20)    NOT NULL DEFAULT 'INVITED',
    invited_by          UUID           NULL,
    invited_at          TIMESTAMPTZ    NULL,
    accepted_at         TIMESTAMPTZ    NULL,
    -- TK-05 / IMP-04: access tokens emitidos antes deste instante são rejeitados.
    role_changed_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    default_hourly_cost NUMERIC(19, 4) NULL,
    -- ART-041: nenhuma coluna monetária sem coluna de moeda.
    cost_currency       CHAR(3)        NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          UUID           NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by          UUID           NULL,
    deleted_at          TIMESTAMPTZ    NULL,
    deleted_by          UUID           NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_memberships PRIMARY KEY (id),
    CONSTRAINT fk_memberships_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_memberships_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_memberships_users_invited_by FOREIGN KEY (invited_by) REFERENCES users (id),
    -- Role inclui CLIENT_PORTAL, reservado para v2.x (permissions.md §5).
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'MEMBER', 'VIEWER', 'CLIENT_PORTAL')),
    CONSTRAINT ck_memberships_status CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')),
    CONSTRAINT ck_memberships_hourly_cost CHECK (default_hourly_cost IS NULL OR default_hourly_cost >= 0),
    CONSTRAINT ck_memberships_cost_currency CHECK (default_hourly_cost IS NULL OR cost_currency IS NOT NULL),
    -- INV-MEM-04: status ACTIVE exige acceptedAt preenchido.
    CONSTRAINT ck_memberships_active_requires_accepted CHECK (status <> 'ACTIVE' OR accepted_at IS NOT NULL)
);

-- INV-MEM-01: (tenantId, userId) único entre memberships não excluídos.
CREATE UNIQUE INDEX uq_memberships_tenant_user
    ON memberships (tenant_id, user_id) WHERE deleted_at IS NULL;

-- Suporta a listagem de tenants disponíveis para o usuário no login (@CrossTenant justificado).
CREATE INDEX idx_memberships_user_status
    ON memberships (user_id, status) WHERE deleted_at IS NULL;

-- INV-MEM-02: suporta a verificação de existência de OWNER ativo por tenant.
CREATE INDEX idx_memberships_tenant_role
    ON memberships (tenant_id, role) WHERE deleted_at IS NULL AND status = 'ACTIVE';
