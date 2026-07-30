-- V005 — refresh_tokens (database.md §7.12, entities.md §6.19).
--
-- tenant_id é anulável por decisão explícita de database.md §7.12 ("sem tenant_id
-- obrigatório"): a renovação de token pode preceder a seleção de tenant, quando o usuário
-- pertence a mais de um (token de pré-seleção, security.md §3).

CREATE TABLE refresh_tokens (
    id             UUID         NOT NULL,
    tenant_id      UUID         NULL,
    user_id        UUID         NOT NULL,
    -- RT-02: apenas o SHA-256 do token é persistido; o valor bruto nunca é armazenado.
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ  NULL,
    -- RT-03: cada uso emite um novo token e marca o anterior, formando a cadeia de rotação.
    replaced_by_id UUID         NULL,
    user_agent     VARCHAR(400) NULL,
    -- 45 caracteres acomodam IPv6 com sufixo IPv4 mapeado.
    ip_address     VARCHAR(45)  NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     UUID         NULL,
    deleted_at     TIMESTAMPTZ  NULL,
    deleted_by     UUID         NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id),
    CONSTRAINT ck_refresh_tokens_hash_length CHECK (length(token_hash) = 64),
    CONSTRAINT ck_refresh_tokens_expires_after_created CHECK (expires_at > created_at)
);

-- RT-01/RT-02: a busca de renovação ocorre pelo hash. Único, não parcial: um hash já
-- emitido nunca pode reaparecer, mesmo excluído logicamente — a colisão indicaria falha
-- de geração aleatória, que precisa ser rejeitada pelo banco.
CREATE UNIQUE INDEX uq_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- Suporta o job de limpeza de tokens expirados (RT-08).
CREATE INDEX idx_refresh_tokens_user_expires ON refresh_tokens (user_id, expires_at)
    WHERE deleted_at IS NULL;

-- Suporta a revogação em cadeia por usuário na detecção de reuso (RT-04 / RN-005).
CREATE INDEX idx_refresh_tokens_user_revoked ON refresh_tokens (user_id, revoked_at)
    WHERE deleted_at IS NULL;
