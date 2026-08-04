-- V031 — índices do painel (specs/010-dashboard §13.4).
--
-- Única migration da feature: o painel é integralmente de leitura (RS-01) e não persiste nada.
--
-- Numeração: specs/010-dashboard §13.3 prevê `V032`. A sequência real do repositório vai até
-- `V030`, e `V031` é o próximo número livre. Vale a mesma resolução registrada em V016 e nas notas
-- ¹ e ² de database.md §8.1: a numeração das specs foi planejada sobre uma sequência hipotética e
-- a numeração efetiva segue o próximo livre, porque ART-053 impede renumerar depois do merge.
--
-- Os três primeiros índices usam INCLUDE para virarem índices COBERTOS: a soma é resolvida
-- percorrendo apenas o índice, sem acessar a tabela. É o que sustenta a meta de p95 < 800 ms com
-- 100.000 registros (RNF-003, RP-06) — sem eles as agregações cairiam nos índices de listagem de
-- 008, que não cobrem as colunas somadas e obrigariam um heap fetch por linha agregada.
--
-- `billable_minutes` não existe como coluna: RN-112 a define como derivada
-- (`billable ? net_minutes : 0`), e ART-034 mantém apenas minutos inteiros persistidos. O índice
-- carrega `billable`, que é o que a expressão precisa para ser resolvida sem tocar na tabela.

-- charts.dailyMinutes e quickStats (hoje, semana, período).
CREATE INDEX idx_work_logs_dashboard_daily
    ON work_logs (tenant_id, work_date, user_id)
    INCLUDE (net_minutes, billable)
    WHERE deleted_at IS NULL;

-- charts.byClient.
CREATE INDEX idx_work_logs_dashboard_client
    ON work_logs (tenant_id, work_date, client_id)
    INCLUDE (net_minutes, billable)
    WHERE deleted_at IS NULL;

-- charts.byCategory.
CREATE INDEX idx_work_logs_dashboard_category
    ON work_logs (tenant_id, work_date, category_id)
    INCLUDE (net_minutes, billable)
    WHERE deleted_at IS NULL;

-- Cartões de contrato: apenas ACTIVE e SUSPENDED aparecem no painel. Índice parcial porque o
-- conjunto que interessa é uma fração do total — DRAFT, ENDED e CANCELLED acumulam ao longo do
-- tempo e nunca são lidos aqui.
CREATE INDEX idx_contracts_active_dashboard
    ON contracts (tenant_id, status)
    WHERE status IN ('ACTIVE', 'SUSPENDED') AND deleted_at IS NULL;

-- openTickets. specs/010 §13.4 registra este índice como "criado em 007", mas V014 criou apenas
-- `idx_tickets_tenant_assignee`, sem o recorte de estado. A listagem do painel filtra por
-- responsável E por estado aberto; sem o recorte, um responsável com histórico longo de tickets
-- concluídos leria todos eles para descartar quase todos.
CREATE INDEX idx_tickets_open_assignee
    ON tickets (tenant_id, assignee_id, status)
    WHERE status NOT IN ('DONE', 'CANCELLED') AND deleted_at IS NULL;
