-- V003 — users (database.md §7.2, entities.md §6.2).
--
-- Identidade autenticável. Não é tenant-scoped: um usuário pode pertencer a vários tenants
-- (exceção declarada em database.md §4.3 e ART-013).

CREATE TABLE users (
    id                    UUID         NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    -- ART-081 / PW-01: BCrypt custo 12. VARCHAR(72) é o limite do algoritmo.
    password_hash         VARCHAR(72)  NOT NULL,
    full_name             VARCHAR(150) NOT NULL,
    display_name          VARCHAR(60)  NULL,
    avatar_url            VARCHAR(500) NULL,
    status                VARCHAR(25)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    email_verified_at     TIMESTAMPTZ  NULL,
    last_login_at         TIMESTAMPTZ  NULL,
    failed_login_attempts SMALLINT     NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ  NULL,
    -- TK-04: tokens emitidos antes deste instante são rejeitados.
    password_changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    timezone              VARCHAR(60)  NULL,
    locale                VARCHAR(10)  NULL,
    preferences           JSONB        NOT NULL DEFAULT '{}',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            UUID         NULL,
    deleted_at            TIMESTAMPTZ  NULL,
    deleted_by            UUID         NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_full_name_length CHECK (length(full_name) BETWEEN 2 AND 150),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0),
    -- INV-USR-03: status LOCKED exige lockedUntil preenchido.
    CONSTRAINT ck_users_locked_requires_until CHECK (status <> 'LOCKED' OR locked_until IS NOT NULL)
);

-- INV-USR-01: e-mail único entre usuários não excluídos.
-- AU-03: o e-mail é normalizado em minúsculas antes da busca; o índice usa lower() para
-- impedir contas duplicadas por diferença de caixa.
CREATE UNIQUE INDEX uq_users_email ON users (lower(email)) WHERE deleted_at IS NULL;

CREATE INDEX idx_users_status ON users (status) WHERE deleted_at IS NULL;
