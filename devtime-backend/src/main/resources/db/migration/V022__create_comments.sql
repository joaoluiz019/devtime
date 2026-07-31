-- V022 — comments (database.md §7.12, entities.md §6.16).
--
-- Conversa do ticket e registro automático de mudanças estruturais (RN-815). Numeração conforme
-- database.md §8.1 (V022 = comments), que prevalece sobre specs/014-comments/tasks.md T-014-01
-- (V037) pela hierarquia IA-11.

CREATE TABLE comments (
    id                 UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    ticket_id          UUID        NOT NULL,
    -- Nulo apenas em comentário de sistema: não há pessoa a quem atribuir a autoria (CE-P-08).
    author_id          UUID        NULL,
    body               TEXT        NOT NULL,
    -- INV-CMT-01: sempre aponta para uma raiz; a normalização ocorre na escrita (RN-814).
    parent_comment_id  UUID        NULL,
    edited_at          TIMESTAMPTZ NULL,
    mentioned_user_ids UUID[]      NOT NULL DEFAULT '{}',
    -- INV-CMT-03: imutável e inexcluível.
    is_system          BOOLEAN     NOT NULL DEFAULT false,
    -- Gatilho de RN-815; nulo em comentário de usuário.
    system_trigger     VARCHAR(40) NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID        NULL,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         UUID        NULL,
    deleted_at         TIMESTAMPTZ NULL,
    deleted_by         UUID        NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_comments_tickets FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_comments_users FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES comments (id),
    -- RN-811: 1 a 10.000 caracteres após aparar. Rejeita INSERT direto com 0 e com 10.001.
    CONSTRAINT ck_comments_body_length CHECK (length(btrim(body)) BETWEEN 1 AND 10000),
    -- Comentário de usuário exige autor; comentário de sistema exige gatilho.
    CONSTRAINT ck_comments_author CHECK (is_system OR author_id IS NOT NULL),
    CONSTRAINT ck_comments_system_trigger CHECK (is_system = (system_trigger IS NOT NULL)),
    -- RN-815: comentário de sistema nunca é resposta.
    CONSTRAINT ck_comments_system_is_root CHECK (NOT is_system OR parent_comment_id IS NULL),
    -- INV-CMT-01: uma resposta nunca é a si mesma.
    CONSTRAINT ck_comments_parent_not_self CHECK (parent_comment_id IS NULL OR parent_comment_id <> id)
);

-- Listagem por ticket e linha do tempo (spec 014 §13.4).
CREATE INDEX idx_comments_ticket_created
    ON comments (tenant_id, ticket_id, created_at) WHERE deleted_at IS NULL;

-- Respostas de uma raiz, carregadas em lote para evitar N+1.
CREATE INDEX idx_comments_parent
    ON comments (parent_comment_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_comments_author
    ON comments (tenant_id, author_id, created_at DESC) WHERE deleted_at IS NULL;

-- Consulta por menções (RN-813).
CREATE INDEX idx_comments_mentions
    ON comments USING gin (mentioned_user_ids);
