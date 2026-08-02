-- V023 — attachments (database.md §7, entities.md §6.17).
--
-- Numeração conforme database.md §8.1 (V023 = attachments, V024 = índices de F4), que prevalece
-- sobre specs/015-attachments/spec.md §13.3 (V038/V039) pela hierarquia IA-11 — o mesmo critério já
-- aplicado a V022 por 014-comments. A nota ¹ de §8.1 registra que estes dois números estavam
-- reservados justamente a esta feature.

CREATE TABLE attachments (
    id                   UUID         NOT NULL,
    tenant_id            UUID         NOT NULL,
    -- INV-ATT-01: exatamente um dos dois é não nulo. O anexo pertence à unidade de trabalho
    -- (ticket) ou à conversa sobre ela (comentário) — nunca a ambas, nunca a nenhuma.
    ticket_id            UUID         NULL,
    comment_id           UUID         NULL,
    -- RN-804: sanitizado. Nunca é usado para compor storage_key (CP-05).
    file_name            VARCHAR(255) NOT NULL,
    -- Metadado de exibição; preserva o que o usuário enviou, inclusive quando foi sanitizado.
    original_file_name   VARCHAR(255) NOT NULL,
    -- RN-802: allowlist + coincidência com a assinatura binária, ambas na aplicação.
    content_type         VARCHAR(120) NOT NULL,
    size_bytes           BIGINT       NOT NULL,
    -- Identificador opaco gerado pelo sistema (SG-05). 500 por entities.md §6.17.
    storage_key          VARCHAR(500) NOT NULL,
    -- RN-805: deduplicação dentro do tenant e verificação de integridade.
    -- VARCHAR e não CHAR: entities.md §6.17 declara String(64), e CHAR faz o PostgreSQL reportar
    -- `bpchar`, que a validação de schema do Hibernate recusa (ddl-auto = validate, ART-054). O
    -- tamanho exato é garantido pelo CHECK de formato adiante, que é mais forte que o tipo.
    checksum_sha256      VARCHAR(64)  NOT NULL,
    -- §4.9 de state-machines.md. PENDING na criação; o download nasce bloqueado (RN-803).
    scan_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- Máximo de 3 tentativas; a quarta não existe (CP-11).
    attempt_count        INTEGER      NOT NULL DEFAULT 0,
    -- Ameaça identificada, preenchida apenas em INFECTED. Base de investigação de segurança (§18).
    scan_threat          VARCHAR(255) NULL,
    scanned_at           TIMESTAMPTZ  NULL,
    -- IP de quem enviou, exigido pela trilha de ATTACHMENT_SCAN_INFECTED (§18).
    uploaded_from_ip     VARCHAR(45)  NULL,
    uploaded_by          UUID         NOT NULL,
    -- INV-ATT-05/INV-ATT-06: false quando o binário já saiu do storage, por exclusão do último
    -- referenciador ou por infecção. Sem esta coluna, a quota (RN-801) contaria bytes que não
    -- ocupam mais espaço algum e o job de órfãos não teria com o que comparar.
    binary_present       BOOLEAN      NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID         NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           UUID         NULL,
    deleted_at           TIMESTAMPTZ  NULL,
    deleted_by           UUID         NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_attachments PRIMARY KEY (id),
    CONSTRAINT fk_attachments_tenants  FOREIGN KEY (tenant_id)   REFERENCES tenants (id),
    CONSTRAINT fk_attachments_tickets  FOREIGN KEY (ticket_id)   REFERENCES tickets (id),
    CONSTRAINT fk_attachments_comments FOREIGN KEY (comment_id)  REFERENCES comments (id),
    CONSTRAINT fk_attachments_users    FOREIGN KEY (uploaded_by) REFERENCES users (id),
    -- INV-ATT-01, camada 3 (§17.3). O XOR no banco é a última defesa: rejeita INSERT com dois
    -- alvos e com nenhum, inclusive por caminho que não passe pelo serviço.
    CONSTRAINT ck_attachments_single_target
        CHECK ((ticket_id IS NULL) <> (comment_id IS NULL)),
    -- RN-801: 10.485.760 = 10 MB. CX-02 (arquivo de 0 byte) é rejeitado por `> 0`.
    CONSTRAINT ck_attachments_size
        CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    -- §4.9: três tentativas, nunca uma quarta.
    CONSTRAINT ck_attachments_attempt_count
        CHECK (attempt_count >= 0 AND attempt_count <= 3),
    CONSTRAINT ck_attachments_scan_status
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED')),
    -- INV-ATT-06: INFECTED implica binário ausente do storage. A invariante é do domínio; aqui ela
    -- também é do schema, para que nenhuma atualização parcial deixe o par incoerente.
    CONSTRAINT ck_attachments_infected_has_no_binary
        CHECK (scan_status <> 'INFECTED' OR binary_present = false),
    -- A ameaça só existe onde houve ameaça.
    CONSTRAINT ck_attachments_threat_only_when_infected
        CHECK (scan_threat IS NULL OR scan_status = 'INFECTED'),
    -- SHA-256 em hexadecimal minúsculo: 64 caracteres. Impede que um valor truncado ou em outra
    -- codificação passe a participar da deduplicação (RN-805).
    CONSTRAINT ck_attachments_checksum_format
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

COMMENT ON COLUMN attachments.storage_key IS
    'Identificador opaco gerado pelo sistema; nunca derivado de file_name (CP-05, SG-05).';
COMMENT ON COLUMN attachments.binary_present IS
    'false quando o binário já foi removido do storage (INV-ATT-05, INV-ATT-06).';
