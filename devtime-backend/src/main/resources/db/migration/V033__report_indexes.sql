-- V033 — índices de acompanhamento, fila e expiração de exportações (specs/012 §13.4).

-- SG-04: a listagem é restrita ao solicitante, e o índice é o que a torna barata. Sem ele, listar
-- as próprias exportações varreria as de todo o tenant e depois descartaria — o que também
-- tornaria a enumeração de exportações de terceiros um problema de custo, e não só de filtro.
CREATE INDEX idx_report_exec_tenant_user
    ON report_executions (tenant_id, requested_by, created_at DESC)
    WHERE deleted_at IS NULL;

-- Fila do ExportProcessorJob (§22.4). Parcial sobre os dois estados que o worker assume: em um
-- tenant com 10.000 exportações concluídas, o índice tem tantas entradas quantas forem as
-- pendentes — e o job roda a cada 30 segundos.
--
-- Sem `tenant_id` à frente, deliberadamente: o job é de plataforma e percorre todos os tenants,
-- definindo o contexto a cada iteração (BR-049). Um índice liderado por tenant obrigaria uma
-- varredura por tenant para achar o que costuma ser um punhado de linhas no total.
CREATE INDEX idx_report_exec_queued
    ON report_executions (status, created_at)
    WHERE status IN ('QUEUED', 'FAILED') AND deleted_at IS NULL;

-- SG-09: o ExportExpiryJob precisa achar o que expirou para REMOVER O BINÁRIO, não apenas marcar o
-- registro. Um arquivo que permanece no storage depois da expiração é dado pessoal fora de
-- qualquer controle de acesso (§19.1).
CREATE INDEX idx_report_exec_expiry
    ON report_executions (expires_at)
    WHERE status = 'COMPLETED' AND deleted_at IS NULL;

-- §20: o relatório de período ABERTO é calculado ao vivo e é o caminho que escala com o volume.
-- `V016` já criou um índice com este nome sobre (tenant_id, contract_id, work_date), mas sem as
-- colunas cobertas. Recriamos no lugar em vez de manter os dois: mesma chave líder, então o segundo
-- índice só custaria escrita.
--
-- `INCLUDE` pelos mesmos motivos de V031: sem as colunas cobertas, a agregação volta à heap uma vez
-- por linha e a meta de p95 < 1,5 s da §20 não é atingível por otimização de código.
DROP INDEX IF EXISTS idx_work_logs_contract_date;

CREATE INDEX idx_work_logs_contract_date
    ON work_logs (tenant_id, contract_id, work_date)
    INCLUDE (net_minutes, billable, category_id, ticket_id, user_id)
    WHERE deleted_at IS NULL;
