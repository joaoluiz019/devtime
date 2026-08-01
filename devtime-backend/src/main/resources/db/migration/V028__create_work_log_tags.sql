-- V028 — work_log_tags (database.md §7.12, entities.md §6.11).
--
-- database.md §8.1 aloca `ticket_tags` e `work_log_tags` a V017. Apenas `ticket_tags` foi criada
-- lá, porque `work_logs` ainda não existia (o cabeçalho de V017 registra a decisão). Esta é a
-- migration incremental prevista por CE-O-03: a feature de menor ordem cria a migration, a de
-- maior ordem cria a incremental. V017 não é alterada (BR-035, ART-053, IA-03), e o número é o
-- próximo livre da faixa — a mesma solução adotada pela nota ¹ de database.md §8.1 para
-- verification_tokens e rate_limit_counters.
--
-- Simétrica a ticket_tags: PK composta torna o vínculo idempotente e a tabela não possui colunas
-- de BaseEntity, por ser junção pura sem identidade nem ciclo de vida próprios.

CREATE TABLE work_log_tags (
    work_log_id UUID        NOT NULL,
    tag_id      UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NULL,
    CONSTRAINT pk_work_log_tags PRIMARY KEY (work_log_id, tag_id),
    CONSTRAINT fk_work_log_tags_work_logs FOREIGN KEY (work_log_id) REFERENCES work_logs (id),
    CONSTRAINT fk_work_log_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id),
    CONSTRAINT fk_work_log_tags_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- Contagem de uso e remoção em lote na exclusão da etiqueta (INV-TAG-04).
CREATE INDEX idx_work_log_tags_tag ON work_log_tags (tenant_id, tag_id);
