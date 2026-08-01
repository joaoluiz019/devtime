-- V015 — timers e timer_pauses (database.md §8.1, entities.md §6.14 e §6.15).
--
-- Numeração conforme database.md §8.1 (V015 = timers, timer_pauses), que prevalece sobre
-- specs/009-timer/tasks.md (V026–V028) pela hierarquia IA-11 — a mesma decisão já registrada nos
-- cabeçalhos de V013 e V014.
--
-- A tabela vem antes de work_logs (V016) porque work_logs.timer_id a referencia (INV-WKL-09). A
-- referência inversa — timers.work_log_id — é criada como FK em V016, quando a tabela alvo existe;
-- criá-la aqui exigiria uma ordem impossível entre as duas tabelas.
--
-- Timer NÃO usa exclusão lógica: um cronômetro descartado tem estado próprio (DISCARDED, RN-162) e
-- é isso que o distingue de um registro apagado. As colunas deleted_at/deleted_by existem para
-- satisfazer BaseEntity (ART-050) e permanecem nulas.

CREATE TABLE timers (
    id                          UUID        NOT NULL,
    tenant_id                   UUID        NOT NULL,
    user_id                     UUID        NOT NULL,
    ticket_id                   UUID        NOT NULL,
    category_id                 UUID        NOT NULL,
    status                      VARCHAR(15) NOT NULL DEFAULT 'RUNNING',
    -- 🔒 RN-152: sempre do servidor. Alterá-lo seria reescrever quando o trabalho começou.
    started_at                  TIMESTAMPTZ NOT NULL,
    -- Início do trecho ativo corrente (§6.2 de specs/009).
    last_resumed_at             TIMESTAMPTZ NOT NULL,
    -- Segundos ativos consolidados antes da pausa atual. Serve apenas à exibição em tempo real:
    -- o valor canônico do work log é gross − paused (RN-111, nota de §6.1 de business-rules.md).
    accumulated_active_seconds  INTEGER     NOT NULL DEFAULT 0,
    -- 💾 RN-156: soma das pausas concluídas, recalculada a cada retomada.
    paused_minutes              INTEGER     NOT NULL DEFAULT 0,
    description                 TEXT        NULL,
    billable                    BOOLEAN     NOT NULL DEFAULT TRUE,
    stopped_at                  TIMESTAMPTZ NULL,
    work_log_id                 UUID        NULL,
    -- RN-163: evita que o job notifique o mesmo timer longo mais de uma vez.
    long_running_notified_at    TIMESTAMPTZ NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID        NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID        NULL,
    deleted_at                  TIMESTAMPTZ NULL,
    deleted_by                  UUID        NULL,
    version                     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_timers PRIMARY KEY (id),
    CONSTRAINT fk_timers_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_timers_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_timers_tickets FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_timers_categories FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_timers_status CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'DISCARDED', 'ABANDONED')),
    CONSTRAINT ck_timers_accumulated CHECK (accumulated_active_seconds >= 0),
    CONSTRAINT ck_timers_paused_minutes CHECK (paused_minutes >= 0),
    CONSTRAINT ck_timers_description_length CHECK (description IS NULL OR length(description) <= 2000),
    -- INV-TMR-04.
    CONSTRAINT ck_timers_completed CHECK (status <> 'COMPLETED' OR (work_log_id IS NOT NULL AND stopped_at IS NOT NULL)),
    -- INV-TMR-05: descarte nunca gera work log (RN-162).
    CONSTRAINT ck_timers_discarded CHECK (status <> 'DISCARDED' OR work_log_id IS NULL)
);

-- RN-150 / INV-TMR-01: no máximo um timer ativo por usuário.
--
-- O índice é sobre (user_id) SEM tenant_id, deliberadamente (§13.3 de specs/009): RN-150 vale
-- entre TODOS os tenants do usuário (CE-13). Incluir tenant_id permitiria dois cronômetros ativos
-- em tenants distintos, que é exatamente o que a regra proíbe.
--
-- Diferentemente de RN-102 em work_logs — onde a constraint colide com o soft delete (OB-02 de
-- specs/008) — aqui ela é viável: Timer não usa exclusão lógica e o predicado é sobre status.
CREATE UNIQUE INDEX uq_timers_active_user
    ON timers (user_id) WHERE status IN ('RUNNING', 'PAUSED');

-- GET /timers/active (TIMER_VIEW_ANY).
CREATE INDEX idx_timers_tenant_status ON timers (tenant_id, status);

-- TimerMonitorJob (RN-163, RN-164): percorre timers ativos de todos os tenants.
CREATE INDEX idx_timers_monitor
    ON timers (status, started_at) WHERE status IN ('RUNNING', 'PAUSED');

-- RN-311 (conclusão de ticket) e RN-240 (fechamento de período).
CREATE INDEX idx_timers_ticket
    ON timers (tenant_id, ticket_id) WHERE status IN ('RUNNING', 'PAUSED');

-- RN-165: expiração da janela de 7 dias para recuperação.
CREATE INDEX idx_timers_abandoned
    ON timers (status, started_at) WHERE status = 'ABANDONED';

CREATE TABLE timer_pauses (
    id               UUID         NOT NULL,
    tenant_id        UUID         NOT NULL,
    timer_id         UUID         NOT NULL,
    paused_at        TIMESTAMPTZ  NOT NULL,
    resumed_at       TIMESTAMPTZ  NULL,
    duration_seconds INTEGER      NULL,
    -- §19.1 de specs/009: opcional e nunca obrigatório. Exigir justificativa de pausa
    -- transformaria o produto em ferramenta de vigilância.
    reason           VARCHAR(200) NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID         NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       UUID         NULL,
    deleted_at       TIMESTAMPTZ  NULL,
    deleted_by       UUID         NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_timer_pauses PRIMARY KEY (id),
    CONSTRAINT fk_timer_pauses_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_timer_pauses_timers FOREIGN KEY (timer_id) REFERENCES timers (id),
    CONSTRAINT ck_timer_pauses_range CHECK (resumed_at IS NULL OR resumed_at > paused_at),
    CONSTRAINT ck_timer_pauses_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

-- INV-TMR-02 / INV-TMR-03: no máximo uma pausa aberta por timer.
CREATE UNIQUE INDEX uq_timer_pauses_open
    ON timer_pauses (timer_id) WHERE resumed_at IS NULL;

CREATE INDEX idx_timer_pauses_timer ON timer_pauses (tenant_id, timer_id);
