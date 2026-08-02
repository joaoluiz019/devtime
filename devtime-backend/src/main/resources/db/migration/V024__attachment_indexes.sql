-- V024 — índices de attachments (spec 015 §13.4; database.md §8.1 reserva este número aos
-- "índices de performance adicionais" de F4).

-- Anexos do ticket e contagem de RN-806 (20 por ticket).
CREATE INDEX idx_attachments_ticket
    ON attachments (tenant_id, ticket_id)
    WHERE ticket_id IS NOT NULL AND deleted_at IS NULL;

-- Anexos do comentário e contagem de RN-806 (5 por comentário).
CREATE INDEX idx_attachments_comment
    ON attachments (tenant_id, comment_id)
    WHERE comment_id IS NOT NULL AND deleted_at IS NULL;

-- RN-805: deduplicação. O tenant_id vem primeiro porque a busca é sempre dentro do tenant — a
-- deduplicação entre tenants criaria canal de inferência (§6.4, CP-06).
CREATE INDEX idx_attachments_checksum
    ON attachments (tenant_id, checksum_sha256)
    WHERE deleted_at IS NULL;

-- Contagem de referências na exclusão (RN-805, passo 5 de §6.4). Deliberadamente **sem**
-- tenant_id: a storage_key é única no storage, e o que decide a remoção do binário é a existência
-- de qualquer outro registro que a referencie.
CREATE INDEX idx_attachments_storage_key
    ON attachments (storage_key)
    WHERE deleted_at IS NULL;

-- Fila de verificação do ScanWorkerJob. Também sem tenant_id: o worker percorre todos os tenants e
-- define o contexto a cada item (BR-049).
CREATE INDEX idx_attachments_scan_queue
    ON attachments (scan_status, created_at)
    WHERE scan_status IN ('PENDING', 'FAILED') AND deleted_at IS NULL;

-- Quota do tenant (RN-801), resolvida sem tocar na tabela.
--
-- §13.4 especifica `INCLUDE (size_bytes)`. `binary_present` entra junto porque CX-18 determina que
-- a quota conte "apenas registros não excluídos e binários presentes": sem a coluna no índice, o
-- predicado obrigaria a visitar o heap e o índice deixaria de ser coberto — perdendo exatamente a
-- propriedade que §20 exige dele (< 50 ms).
CREATE INDEX idx_attachments_quota
    ON attachments (tenant_id)
    INCLUDE (size_bytes, binary_present)
    WHERE deleted_at IS NULL;
