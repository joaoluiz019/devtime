-- V017 — ticket_tags (database.md §7.12, entities.md §6.11).
--
-- database.md §8.1 aloca V017 a `ticket_tags` e `work_log_tags`. Apenas `ticket_tags` é criada
-- aqui: `work_log_tags` referencia `work_logs`, que só existe a partir de 008-worklogs (V016).
-- Aplicação direta de CE-O-03 — a feature de menor ordem cria a migration, a de maior ordem cria a
-- incremental. Reservar o número agora violaria SQ-05.
--
-- A PK composta é o que torna o vínculo idempotente (CX-10 de 006-tags): vincular duas vezes a
-- mesma tag ao mesmo ticket não cria segunda linha e não incrementa usageCount.
--
-- A tabela não possui colunas de BaseEntity: é uma tabela de junção pura, sem identidade própria
-- nem ciclo de vida. `tenant_id` está presente porque ART-013 o exige em todo dado de negócio e
-- porque a remoção em massa na exclusão de uma tag é feita por (tenant_id, tag_id).

CREATE TABLE ticket_tags (
    ticket_id  UUID        NOT NULL,
    tag_id     UUID        NOT NULL,
    tenant_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID        NULL,
    CONSTRAINT pk_ticket_tags PRIMARY KEY (ticket_id, tag_id),
    CONSTRAINT fk_ticket_tags_tickets FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_ticket_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id),
    CONSTRAINT fk_ticket_tags_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- Contagem de uso e remoção em lote na exclusão da tag (§20 de 006-tags).
CREATE INDEX idx_ticket_tags_tag
    ON ticket_tags (tenant_id, tag_id);
