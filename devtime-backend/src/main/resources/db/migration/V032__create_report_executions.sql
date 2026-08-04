-- V032 — report_executions (database.md §8.1, specs/012 §13, state-machines.md §4.10).
--
-- NUMERAÇÃO. database.md §8.1 reserva `V021` a esta tabela e specs/012 §13.3 prevê `V033`/`V034`.
-- Nenhum dos dois é aplicável: `V021` ficou vago quando `020-period_snapshots` foi seguida por
-- `V022`, e preencher um vão exigiria `out-of-order`, que `application.yml` desliga
-- (`validate-on-migrate: true`) — todo banco que já aplicou de `V022` a `V031` recusaria migrar.
-- A sequência real termina em `V031` e o próximo livre é `V032`, mesma resolução de `V029`, `V030`
-- e `V031`. `V021` permanece permanentemente vago; um número de migration nunca é reaproveitado.

CREATE TABLE report_executions (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    -- 🔒 Imutáveis após a criação (§13.2): a execução registra o que foi pedido, e alterar o
    -- pedido depois do fato destruiria a reprodutibilidade que RN-707 existe para garantir.
    report_type     VARCHAR(30)  NOT NULL,
    format          VARCHAR(10)  NOT NULL,
    -- RN-707: os filtros aplicados, para reprodutibilidade. Sem eles a execução diria "alguém
    -- exportou um PDF" sem dizer de quê — inútil em investigação (§18).
    parameters      JSONB        NOT NULL DEFAULT '{}',
    -- reports.md §8.1: página de rosto, gráficos e idioma. Separado de `parameters` porque não
    -- participa da identidade do relatório: dois PDFs do mesmo período com e sem página de rosto
    -- contêm os mesmos dados.
    options         JSONB        NOT NULL DEFAULT '{}',
    -- 🔒 ART-074 / CE-R-12: duas requisições idênticas devolvem a MESMA exportação. Sem esta
    -- coluna, um duplo clique geraria dois arquivos e duas cobranças de rate limit.
    idempotency_key VARCHAR(120) NULL,
    -- 🔒 Nunca vem da requisição (§13.2): é o autenticado.
    requested_by    UUID         NOT NULL,
    status          VARCHAR(15)  NOT NULL,
    row_count       INTEGER      NULL,
    -- reports.md §8.2: progresso observável durante PROCESSING.
    processed_rows  INTEGER      NULL,
    file_name       VARCHAR(255) NULL,
    size_bytes      BIGINT       NULL,
    storage_key     VARCHAR(500) NULL,
    attempt_count   SMALLINT     NOT NULL DEFAULT 0,
    failure_reason  TEXT         NULL,
    completed_at    TIMESTAMPTZ  NULL,
    -- §4.10 e §19.1: o arquivo é de uso imediato. Retê-lo indefinidamente criaria um repositório
    -- paralelo de dado pessoal fora do banco, com controle de acesso mais fraco.
    expires_at      TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      UUID         NULL,
    deleted_at      TIMESTAMPTZ  NULL,
    deleted_by      UUID         NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_report_executions PRIMARY KEY (id),
    CONSTRAINT fk_report_executions_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    -- §17.3: o solicitante é rastreável mesmo depois de deixar o tenant. É o que responde "quem
    -- exportou este arquivo" quando a pergunta aparece meses depois.
    CONSTRAINT fk_report_executions_users FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_report_executions_type CHECK (
        report_type IN ('CONTRACT_PERIOD', 'CLIENT_SUMMARY', 'TIMESHEET', 'TICKET_DETAIL',
                        'PRODUCTIVITY')),
    CONSTRAINT ck_report_executions_format CHECK (format IN ('PDF', 'XLSX', 'CSV')),
    CONSTRAINT ck_report_executions_status CHECK (
        status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    -- §4.10 e CP-16: duas falhas indicam um problema que uma terceira tentativa não resolve. O
    -- CHECK é a garantia estrutural — um defeito no contador do job esbarra no banco, não em
    -- um `if` que alguém pode remover.
    CONSTRAINT ck_report_executions_attempts CHECK (attempt_count BETWEEN 0 AND 2),
    -- INV-RPT-05: COMPLETED sem chave de storage é uma exportação que se declara pronta e não
    -- tem arquivo. O download responderia 302 para lugar nenhum.
    CONSTRAINT ck_report_executions_completed_key CHECK (
        status <> 'COMPLETED' OR storage_key IS NOT NULL),
    CONSTRAINT ck_report_executions_row_count CHECK (row_count IS NULL OR row_count >= 0)
);

-- ART-074 / CE-R-12. Parcial por `deleted_at`: uma exportação cancelada (§11, soft delete) libera
-- a chave, porque cancelar é dizer "não quero mais esta", e repetir o pedido depois é legítimo.
CREATE UNIQUE INDEX uq_report_exec_idempotency
    ON report_executions (tenant_id, requested_by, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;
