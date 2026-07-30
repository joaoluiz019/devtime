-- V006 — audit_logs particionada + partições iniciais (database.md §7.11 e §8.1, fase F0).
--
-- INV-AUD-01: append-only. Não possui updated_*, deleted_* nem version (database.md §4.3).
-- A chave primária inclui occurred_at porque PostgreSQL exige que a coluna de
-- particionamento faça parte de toda constraint única da tabela particionada.

CREATE TABLE audit_logs (
    id           UUID        NOT NULL,
    tenant_id    UUID        NOT NULL,
    actor_id     UUID        NULL,
    actor_type   VARCHAR(15) NOT NULL DEFAULT 'USER',
    action       VARCHAR(60) NOT NULL,
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    UUID        NOT NULL,
    before_state JSONB       NULL,
    after_state  JSONB       NULL,
    -- Conteúdo mínimo de security.md §10.2: traceId, ipAddress, userAgent, result.
    -- Dados sensíveis são proibidos aqui (ART-084).
    metadata     JSONB       NOT NULL DEFAULT '{}',
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID        NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id, occurred_at),
    CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'API_KEY')),
    -- CE-S-06: ação de sistema não possui usuário; qualquer outro tipo de ator exige um.
    CONSTRAINT ck_audit_logs_actor_required CHECK (actor_type <> 'USER' OR actor_id IS NOT NULL)
) PARTITION BY RANGE (occurred_at);

CREATE INDEX idx_audit_tenant_entity
    ON audit_logs (tenant_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_tenant_actor
    ON audit_logs (tenant_id, actor_id, occurred_at DESC);

-- Partições iniciais: 12 meses explícitos, sem partição DEFAULT.
-- Uma partição DEFAULT impediria o job mensal de anexar partições no intervalo já coberto
-- por ela, quebrando o particionamento previsto em database.md §7.11.
CREATE TABLE audit_logs_2026_07 PARTITION OF audit_logs FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE audit_logs_2026_08 PARTITION OF audit_logs FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE audit_logs_2026_09 PARTITION OF audit_logs FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_logs_2026_10 PARTITION OF audit_logs FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE audit_logs_2026_11 PARTITION OF audit_logs FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE audit_logs_2026_12 PARTITION OF audit_logs FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE audit_logs_2027_01 PARTITION OF audit_logs FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE audit_logs_2027_02 PARTITION OF audit_logs FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE audit_logs_2027_03 PARTITION OF audit_logs FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE audit_logs_2027_04 PARTITION OF audit_logs FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE audit_logs_2027_05 PARTITION OF audit_logs FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE audit_logs_2027_06 PARTITION OF audit_logs FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');

-- CE-DB-06: tentativa de UPDATE ou DELETE em audit_logs é bloqueada por permissão.
-- Guardado pela existência do papel porque a topologia de papéis do banco (qual usuário a
-- aplicação usa e quem é o dono das tabelas) não está especificada em database.md; sem o
-- papel a migration precisa continuar aplicável em banco limpo (CA-05).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'devtime_app') THEN
        REVOKE UPDATE, DELETE ON audit_logs FROM devtime_app;
    END IF;
END
$$;
