-- V009 — tags (database.md §7.12, entities.md §6.11).
--
-- Vocabulário livre de rótulos normalizados do tenant. Oposto deliberado da categoria: opcional,
-- múltiplo e descartável.
--
-- Numeração conforme database.md §8.1 (V009 = tags), que prevalece sobre
-- specs/006-tags/tasks.md T-006-01 (V017) pela hierarquia IA-11: 03-architecture/ precede specs/.
-- V017 está reservada às tabelas de vínculo.

CREATE TABLE tags (
    id          UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    -- INV-TAG-03: sempre em forma normalizada (RN-506). A normalização ocorre no serviço.
    name        VARCHAR(40) NOT NULL,
    color       CHAR(7)     NOT NULL DEFAULT '#94A3B8',
    -- INV-TAG-04: desnormalizado; muda por efeito de vínculo e é reconciliado.
    usage_count INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  UUID        NULL,
    deleted_at  TIMESTAMPTZ NULL,
    deleted_by  UUID        NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT fk_tags_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    -- RN-507: comprimento verificado sobre o nome já normalizado.
    CONSTRAINT ck_tags_name_length CHECK (length(name) BETWEEN 2 AND 40),
    -- INV-TAG-03: nome normalizado não tem espaço nem maiúscula.
    CONSTRAINT ck_tags_name_normalized CHECK (name = lower(name) AND position(' ' IN name) = 0),
    CONSTRAINT ck_tags_color_format CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_tags_usage_count CHECK (usage_count >= 0)
);

-- INV-TAG-02 / RN-507: unicidade do nome normalizado por tenant.
-- Parcial (ART-055): permite recriar uma tag cujo registro anterior foi excluído (CX-08).
CREATE UNIQUE INDEX uq_tags_tenant_name
    ON tags (tenant_id, name) WHERE deleted_at IS NULL;

-- Ordenação padrão de users.md §9.1 e filtro minUsage.
CREATE INDEX idx_tags_tenant_usage
    ON tags (tenant_id, usage_count DESC, name) WHERE deleted_at IS NULL;

-- RN-508: tags órfãs candidatas a sugestão de limpeza. Índice parcial sobre usage_count = 0.
CREATE INDEX idx_tags_tenant_orphan
    ON tags (tenant_id, updated_at) WHERE usage_count = 0 AND deleted_at IS NULL;
