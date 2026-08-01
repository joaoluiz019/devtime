-- V019 — notifications (database.md §8.1, entities.md §6.18).
--
-- Numeração conforme database.md §8.1 (V019 = notifications), que prevalece sobre
-- specs/013-notifications/tasks.md (V035/V036) pela hierarquia IA-11 — mesma decisão registrada em
-- V013, V014, V015 e V016.
--
-- Os dois conteúdos previstos naquelas duas migrations — tabela e índices — são entregues aqui.
-- Separá-los não acrescenta garantia alguma e ART-053 tornaria a divisão irreversível.

CREATE TABLE notifications (
    id             UUID         NOT NULL,
    tenant_id      UUID         NOT NULL,
    -- 🔒 RN-607: sempre uma pessoa, nunca um papel. A resolução de destinatários acontece na
    -- criação, e não na leitura, porque quem era ADMIN naquele momento é o que importa (CX-10).
    recipient_id   UUID         NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    severity       VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    title          VARCHAR(150) NOT NULL,
    -- §19.1: sem descrições de work log e sem valores monetários. O corpo vai para um provedor de
    -- e-mail externo e pode ser armazenado fora do controle do tenant.
    body           VARCHAR(500) NOT NULL,
    payload        JSONB        NOT NULL DEFAULT '{}',
    entity_type    VARCHAR(40)  NULL,
    entity_id      UUID         NULL,
    -- 🔒 RN-601: identificador lógico do evento. Formato {type}:{entityId}:{discriminador}.
    dedupe_key     VARCHAR(200) NOT NULL,
    read_at        TIMESTAMPTZ  NULL,
    email_sent_at  TIMESTAMPTZ  NULL,
    -- RN-610: contador por notificação, exigido pela idempotência do EmailRetryJob (§22.4 da
    -- spec). Sem ele não há como limitar a três tentativas — entities.md §6.18 não o declara, e a
    -- lacuna está registrada no CHANGELOG.
    email_attempts SMALLINT     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     UUID         NULL,
    deleted_at     TIMESTAMPTZ  NULL,
    deleted_by     UUID         NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_notifications_users FOREIGN KEY (recipient_id) REFERENCES users (id),
    CONSTRAINT ck_notifications_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_notifications_title CHECK (length(btrim(title)) BETWEEN 1 AND 150),
    CONSTRAINT ck_notifications_body CHECK (length(btrim(body)) BETWEEN 1 AND 500),
    -- RN-610: no máximo três tentativas; a quarta é proibida (CP-08).
    CONSTRAINT ck_notifications_email_attempts CHECK (email_attempts BETWEEN 0 AND 3)
);

-- RN-601 / INV-NOT-01 — A GARANTIA ESTRUTURAL DA FEATURE.
--
-- Sem este índice, duas avaliações concorrentes do mesmo limiar criariam duas notificações
-- idênticas e o `dedupeKey` seria apenas uma convenção. Com ele, a inserção pode ser tentada
-- diretamente: a violação é tratada como sucesso silencioso, o que elimina a janela de corrida
-- entre verificar e inserir (CP-03).
--
-- Não é parcial por `deleted_at`: uma notificação excluída pelo usuário não deve ser recriada pela
-- avaliação seguinte do mesmo limiar. Excluir é dizer "já vi isso", e RN-601 vale para sempre.
CREATE UNIQUE INDEX uq_notifications_recipient_dedupe
    ON notifications (recipient_id, dedupe_key);

-- Listagem da central, ordenação fixa por `createdAt DESC` (§7 de notifications.md).
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC) WHERE deleted_at IS NULL;

-- Contagem de não lidas — índice PARCIAL, e é isso que a torna barata: em um usuário com 5.000
-- notificações e 3 não lidas, o índice tem 3 entradas. É consultada ao carregar toda tela.
CREATE INDEX idx_notifications_unread
    ON notifications (recipient_id) WHERE read_at IS NULL AND deleted_at IS NULL;

-- Fila de reprocessamento de e-mail (RN-610).
CREATE INDEX idx_notifications_email_pending
    ON notifications (created_at)
    WHERE email_sent_at IS NULL AND email_attempts < 3 AND deleted_at IS NULL;

-- RN-609: apenas LIDAS são purgadas. Uma não lida nunca entra neste índice, e é por isso que ela
-- nunca é removida — purgar um alerta que ninguém viu esconderia a informação de que ele existiu.
CREATE INDEX idx_notifications_purge
    ON notifications (read_at) WHERE read_at IS NOT NULL AND deleted_at IS NULL;
