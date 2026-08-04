package com.devtime.report.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Registro de uma exportação (RN-707, §13 de specs/012, §4.10 de state-machines.md).
 *
 * <p>INV-RPT-05: <b>todo</b> arquivo exportado tem uma execução correspondente. É o que responde
 * "quem exportou o quê, com quais filtros" quando a pergunta aparece meses depois — e §18 registra
 * que sem os filtros a resposta seria "alguém exportou um PDF", inútil em investigação.
 *
 * <p>O pedido é imutável ({@code updatable = false} em {@code reportType}, {@code format}, {@code
 * parameters}, {@code options}, {@code idempotencyKey} e {@code requestedBy}). Alterar o pedido
 * depois do fato destruiria a reprodutibilidade que RN-707 existe para garantir. Mudam apenas os
 * campos da máquina de §4.10, e só pelo serviço e pelos jobs.
 */
@Entity
@Table(name = "report_executions")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class ReportExecution extends TenantScopedEntity {

    /** 🔒 §13.2. */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30, updatable = false)
    private ReportType reportType;

    /** 🔒 §13.2. */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10, updatable = false)
    private ExportFormat format;

    /**
     * 🔒 RN-707: os filtros aplicados, serializados.
     *
     * <p>Guardado como texto JSON e não como estrutura tipada porque os cinco tipos de relatório
     * têm conjuntos de parâmetros diferentes, e uma coluna por parâmetro produziria uma tabela
     * larga e majoritariamente nula. O preço é que a leitura precisa desserializar — aceitável,
     * porque a única leitura é a exibição em §8.2 e a reprodução de OB-08.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false, updatable = false)
    private String parameters;

    /** 🔒 §8.1: página de rosto, gráficos e idioma. Não participa da identidade do relatório. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false, updatable = false)
    private String options;

    /** 🔒 ART-074 / CE-R-12: duas requisições idênticas devolvem a mesma exportação. */
    @Column(name = "idempotency_key", length = 120, updatable = false)
    private String idempotencyKey;

    /** 🔒 §13.2: o autenticado, nunca a requisição. */
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ExportStatus status;

    /** Contagem real de linhas do resultado filtrado (§6.2, passo 10). */
    @Column(name = "row_count")
    private Integer rowCount;

    /** §8.2: progresso observável durante {@code PROCESSING}. */
    @Column(name = "processed_rows")
    private Integer processedRows;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** INV-RPT-05: não nulo sempre que {@link #status} é {@code COMPLETED} (CHECK em V032). */
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    /** CP-16: no máximo 2, garantido por CHECK — não por um {@code if} que alguém pode remover. */
    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** §19.1: sete dias. Retenção curta porque o relatório é documento de uso imediato. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * QUEUED → PROCESSING: o worker assume e consome uma tentativa.
     *
     * <p>BR-071: a transição é um método de ação, nunca um {@code setStatus} avulso. O contador
     * sobe <b>aqui</b>, na entrada, e não na falha: incrementá-lo ao falhar deixaria uma execução
     * que travou o worker sem consumir tentativa alguma, e ela seria reprocessada para sempre.
     */
    public void markProcessing() {
        this.status = ExportStatus.PROCESSING;
        this.attemptCount = (short) (this.attemptCount + 1);
        this.processedRows = 0;
        this.failureReason = null;
    }

    /** PROCESSING → COMPLETED (§11). {@code expiresAt} é o que o {@code ExportExpiryJob} varre. */
    public void markCompleted(
            String storageKey,
            String fileName,
            long sizeBytes,
            int rowCount,
            Instant completedAt,
            Instant expiresAt) {
        this.status = ExportStatus.COMPLETED;
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.rowCount = rowCount;
        this.processedRows = rowCount;
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * PROCESSING → FAILED, com o motivo (§11).
     *
     * <p>O motivo é exibido ao usuário em §8.3, então nunca carrega mensagem de exceção crua: quem
     * chama traduz antes, para que a resposta não vaze SQL nem nome de tabela (BR-092).
     */
    public void markFailed(String failureReason) {
        this.status = ExportStatus.FAILED;
        this.failureReason = failureReason;
    }

    /** COMPLETED → EXPIRED (§11). Chamado depois de o binário ter sido removido, nunca antes. */
    public void markExpired() {
        this.status = ExportStatus.EXPIRED;
        this.storageKey = null;
    }

    /**
     * CP-16: duas falhas indicam um problema que uma terceira tentativa não resolve.
     *
     * <p>Compara com o teto e não com "menor que", porque o contador já foi incrementado na entrada
     * em {@code PROCESSING}: uma execução em {@code FAILED} com {@code attemptCount = 2} já gastou
     * as duas.
     */
    public boolean hasAttemptsLeft() {
        return attemptCount < MAX_ATTEMPTS;
    }

    /** §4.10 e CHECK {@code ck_report_executions_attempts}. */
    public static final short MAX_ATTEMPTS = 2;
}
