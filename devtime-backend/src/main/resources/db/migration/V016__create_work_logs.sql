-- V016 — work_logs (database.md §8.1, entities.md §6.13).
--
-- A entidade mais crítica do sistema: é o dado que o cliente compra e o número que aparece na
-- fatura. Numeração conforme database.md §8.1 (V016 = work_logs), que prevalece sobre
-- specs/008-worklogs/tasks.md (V022–V025) pela hierarquia IA-11. Os quatro conteúdos previstos
-- naquelas quatro migrations — tabela, índice de sobreposição, demais índices e work_log_tags —
-- são entregues aqui e em V028; separá-los em migrations distintas não acrescenta garantia
-- alguma, e ART-053 torna a divisão irreversível depois do merge.
--
-- NÃO existe constraint EXCLUDE para RN-102 (§13.3 e OB-02 de specs/008). Ela seria a garantia
-- mais forte e é usada em contract_periods (V013), mas aqui colide com o soft delete: um registro
-- excluído logicamente permanece fisicamente na tabela e bloquearia o intervalo, impedindo o
-- usuário de recriar um registro que ele mesmo apagou. A garantia é da aplicação, apoiada em três
-- camadas: OverlapDetector, idx_work_logs_overlap e WorkLogConsistencyJob com alerta crítico.

CREATE TABLE work_logs (
    id                  UUID        NOT NULL,
    tenant_id           UUID        NOT NULL,
    ticket_id           UUID        NOT NULL,
    -- 🔒💾 RN-109 / INV-WKL-06: copiados do ticket na criação e imutáveis. Um relatório passado
    -- não muda porque um ticket foi reclassificado hoje (ART-005).
    contract_id         UUID        NOT NULL,
    client_id           UUID        NOT NULL,
    -- 💾 RN-107: período cujo intervalo fechado [start_date, end_date] contém work_date.
    contract_period_id  UUID        NOT NULL,
    -- 🔒 RN-106 / OWN-01: dono do registro é quem trabalhou, não quem o lançou.
    user_id             UUID        NOT NULL,
    category_id         UUID        NOT NULL,
    -- RN-108: data local de started_at no fuso do tenant; sessão que atravessa a meia-noite
    -- pertence integralmente ao dia de início.
    work_date           DATE        NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    ended_at            TIMESTAMPTZ NOT NULL,
    -- 💾 RN-110: floor((ended_at − started_at)/60); segundos truncados, nunca arredondados.
    gross_minutes       INTEGER     NOT NULL,
    paused_minutes      INTEGER     NOT NULL DEFAULT 0,
    -- 💾 RN-111 + RN-113: gross − paused, arredondado PARA BAIXO ao múltiplo configurado.
    net_minutes         INTEGER     NOT NULL,
    description         TEXT        NOT NULL,
    billable            BOOLEAN     NOT NULL DEFAULT TRUE,
    -- 🔒 RN-126: um work log gerado por cronômetro nunca vira manual.
    source              VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    timer_id            UUID        NULL,
    -- RN-121: preenchido pelo fechamento do período (011); enquanto não nulo, o registro é
    -- imutável.
    locked_at           TIMESTAMPTZ NULL,
    -- RN-123: contra-métrica de qualidade de captura. Índice alto indica formulário ruim.
    edit_count          INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID        NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID        NULL,
    deleted_at          TIMESTAMPTZ NULL,
    deleted_by          UUID        NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_work_logs PRIMARY KEY (id),
    CONSTRAINT fk_work_logs_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_work_logs_tickets FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_work_logs_contracts FOREIGN KEY (contract_id) REFERENCES contracts (id),
    CONSTRAINT fk_work_logs_clients FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_work_logs_periods FOREIGN KEY (contract_period_id) REFERENCES contract_periods (id),
    CONSTRAINT fk_work_logs_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_work_logs_categories FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_work_logs_timers FOREIGN KEY (timer_id) REFERENCES timers (id),
    -- INV-WKL-01.
    CONSTRAINT ck_work_logs_range CHECK (ended_at > started_at),
    -- INV-WKL-02: registro vazio polui o relatório sem informar nada.
    CONSTRAINT ck_work_logs_net_minutes CHECK (net_minutes > 0),
    -- INV-WKL-03 / RN-103: sessão maior que 24h é sempre erro de digitação ou timer esquecido.
    CONSTRAINT ck_work_logs_gross_minutes CHECK (gross_minutes > 0 AND gross_minutes <= 1440),
    -- INV-WKL-04.
    CONSTRAINT ck_work_logs_paused_minutes CHECK (paused_minutes >= 0 AND paused_minutes < gross_minutes),
    -- RN-105: 3 a 2.000 caracteres após aparar as bordas.
    CONSTRAINT ck_work_logs_description CHECK (length(btrim(description)) BETWEEN 3 AND 2000),
    CONSTRAINT ck_work_logs_source CHECK (source IN ('MANUAL', 'TIMER', 'IMPORT', 'AI_SUGGESTION')),
    -- INV-WKL-09.
    CONSTRAINT ck_work_logs_timer_source CHECK (source <> 'TIMER' OR timer_id IS NOT NULL),
    CONSTRAINT ck_work_logs_edit_count CHECK (edit_count >= 0)
);

-- RN-102 — O ÍNDICE MAIS CRÍTICO DA FEATURE.
--
-- A detecção de sobreposição roda em TODA criação e edição, com meta de p95 < 50 ms mesmo com
-- 100.000 registros por tenant (§20 de specs/008). Com (tenant_id, user_id, started_at, ended_at)
-- a busca vira uma varredura de faixa restrita a um único usuário — tipicamente poucas dezenas de
-- linhas, independentemente do tamanho da tabela.
CREATE INDEX idx_work_logs_overlap
    ON work_logs (tenant_id, user_id, started_at, ended_at) WHERE deleted_at IS NULL;

-- RN-219: agregação de consumo do período (reconciliação do fechamento em 011).
CREATE INDEX idx_work_logs_period
    ON work_logs (tenant_id, contract_period_id) WHERE deleted_at IS NULL;

-- RN-308: totais do ticket e listagem por ticket.
CREATE INDEX idx_work_logs_ticket
    ON work_logs (tenant_id, ticket_id) WHERE deleted_at IS NULL;

-- Listagem pessoal e calendário mensal (P21, P22).
CREATE INDEX idx_work_logs_user_date
    ON work_logs (tenant_id, user_id, work_date DESC) WHERE deleted_at IS NULL;

-- RN-505 e estatística de uso de 005-categories, requisito herdado (CE-O-03).
CREATE INDEX idx_work_logs_category
    ON work_logs (tenant_id, category_id) WHERE deleted_at IS NULL;

-- Relatórios por contrato e intervalo (012).
CREATE INDEX idx_work_logs_contract_date
    ON work_logs (tenant_id, contract_id, work_date) WHERE deleted_at IS NULL;

-- Fechamento de período em 011: seleciona apenas o que ainda não foi travado.
CREATE INDEX idx_work_logs_locked
    ON work_logs (tenant_id, contract_period_id) WHERE locked_at IS NULL AND deleted_at IS NULL;

-- Fecha o ciclo de referências entre timers e work_logs. A FK não pôde ser declarada em V015
-- porque a tabela alvo ainda não existia (INV-TMR-04).
ALTER TABLE timers ADD CONSTRAINT fk_timers_work_logs
    FOREIGN KEY (work_log_id) REFERENCES work_logs (id);
