-- V025 — verification_tokens (specs/001-authentication §13.3, T-001-05).
--
-- Tabela de suporte da feature 001. Não constava em database.md §8.1 nem em entities.md §6: a
-- lacuna foi reportada e resolvida acrescentando a tabela ao fim da sequência documentada, e não
-- reaproveitando V006 (já ocupada por `audit_logs`) nem V023/V024, reservadas a `attachments` e
-- aos índices de performance de F4. ART-053: migration mergeada é imutável.
--
-- Um único tipo de registro serve aos três fluxos de token de uso único — verificação de e-mail
-- (7 dias), redefinição de senha (1 hora) e convite (7 dias). Três tabelas teriam schema idêntico
-- e triplicariam o job de limpeza; o que muda entre elas é apenas a política de validade, que é
-- decisão de aplicação e não de schema.

CREATE TABLE verification_tokens (
    id            UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    -- Preenchido apenas em INVITATION: identifica o tenant que convidou (RN-457).
    tenant_id     UUID        NULL,
    type          VARCHAR(20) NOT NULL,
    -- RT-02 aplicado também aqui: o valor bruto viaja no link do e-mail e nunca é persistido.
    token_hash    VARCHAR(64) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    -- RN-461 / PW-06: uso único. Marcado na mesma transação do efeito do token.
    consumed_at   TIMESTAMPTZ NULL,
    -- RN-457: preenchido quando um reenvio substitui este token. Separado de `consumed_at` porque
    -- os dois estados exigem respostas diferentes: um link efetivamente usado responde sucesso na
    -- segunda vez (idempotência de §5.6), enquanto um link substituído por reenvio precisa
    -- responder "expirado" — do contrário o usuário concluiria que o link antigo funcionou.
    invalidated_at TIMESTAMPTZ NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID        NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    UUID        NULL,
    deleted_at    TIMESTAMPTZ NULL,
    deleted_by    UUID        NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_verification_tokens PRIMARY KEY (id),
    CONSTRAINT fk_verification_tokens_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_verification_tokens_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_verification_tokens_type
        CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'INVITATION')),
    CONSTRAINT ck_verification_tokens_hash_length CHECK (length(token_hash) = 64),
    -- INVITATION sempre identifica o tenant de destino; os demais tipos nunca o fazem.
    CONSTRAINT ck_verification_tokens_tenant_scope
        CHECK ((type = 'INVITATION') = (tenant_id IS NOT NULL))
);

-- Único e não parcial, pelo mesmo motivo de uq_refresh_tokens_token_hash: um hash já emitido não
-- pode reaparecer nem após exclusão lógica — a colisão indicaria falha do gerador aleatório.
CREATE UNIQUE INDEX uq_verification_tokens_token_hash ON verification_tokens (token_hash);

-- RN-457: o reenvio invalida o token anterior do mesmo usuário e tipo.
CREATE INDEX idx_verification_tokens_user_type ON verification_tokens (user_id, type)
    WHERE deleted_at IS NULL AND consumed_at IS NULL AND invalidated_at IS NULL;

-- Sustenta VerificationTokenCleanupJob (remoção de expirados e consumidos).
CREATE INDEX idx_verification_tokens_expires ON verification_tokens (expires_at)
    WHERE deleted_at IS NULL;
