package com.devtime.attachment.domain;

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
import org.hibernate.annotations.SQLRestriction;

/**
 * Arquivo anexado a um ticket ou a um comentário (entities.md §6.17).
 *
 * <p>INV-ATT-01: exatamente um de {@code ticketId}/{@code commentId} é não nulo. O artefato
 * pertence à unidade de trabalho ou à conversa sobre ela — um anexo sem alvo não é alcançável por
 * ninguém, e um com dois alvos apareceria duas vezes com uma única contagem para RN-806.
 *
 * <p><b>Quase tudo aqui é imutável (RN-011).</b> Não existe rota de atualização (CP-13): alterar o
 * {@code contentType} depois da verificação permitiria burlar a validação de assinatura, que é a
 * defesa de OB-01. Os únicos campos que mudam são os da máquina de §4.9 — {@code scanStatus},
 * {@code attemptCount}, {@code scanThreat}, {@code scannedAt} e {@code binaryPresent} —, alterados
 * apenas pelo verificador e pela exclusão, nunca por requisição de usuário.
 */
@Entity
@Table(name = "attachments")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Attachment extends TenantScopedEntity {

    /** 🔒 INV-ATT-01: exclusivo com {@link #commentId}. */
    @Column(name = "ticket_id", updatable = false)
    private UUID ticketId;

    /** 🔒 INV-ATT-01: exclusivo com {@link #ticketId}. */
    @Column(name = "comment_id", updatable = false)
    private UUID commentId;

    /** 🔒 RN-804: sanitizado. Nunca participa da composição da {@link #storageKey} (CP-05). */
    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    /** 🔒 RN-804: o que o usuário enviou, preservado como metadado de exibição. */
    @Column(name = "original_file_name", nullable = false, length = 255, updatable = false)
    private String originalFileName;

    /** 🔒 RN-802: na allowlist <b>e</b> coincidente com a assinatura binária. */
    @Column(name = "content_type", nullable = false, length = 120, updatable = false)
    private String contentType;

    /** 🔒 RN-801: até 10 MB. */
    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    /** 🔒 SG-05: identificador opaco gerado pelo sistema. */
    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    /** 🔒 RN-805: deduplicação dentro do tenant e integridade. Nunca exposto (CP-07). */
    @Column(name = "checksum_sha256", nullable = false, length = 64, updatable = false)
    private String checksumSha256;

    /** §4.9: {@code PENDING} na criação; o download nasce bloqueado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 20)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    /** §4.9 / CP-11: no máximo 3. A quarta tentativa não existe. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** §18: ameaça identificada; base de qualquer investigação de segurança. */
    @Column(name = "scan_threat", length = 255)
    private String scanThreat;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    /** §18: IP do upload, exigido pela trilha de {@code ATTACHMENT_SCAN_INFECTED}. */
    @Column(name = "uploaded_from_ip", length = 45, updatable = false)
    private String uploadedFromIp;

    /** 🔒 Origem: usuário autenticado. Nunca da requisição (BR-041). */
    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    /**
     * INV-ATT-05 / INV-ATT-06: {@code false} quando o binário já saiu do storage.
     *
     * <p>Existe porque a exclusão lógica do registro não é suficiente aqui (§19.1, consequência 3):
     * o binário precisa sair de fato, e um registro cuja {@code storageKey} não aponta mais para
     * nada precisa ser distinguível de um que ainda aponta — sem isso, a quota contaria bytes que
     * não ocupam espaço (CX-18) e o job de órfãos não teria com o que comparar.
     */
    @Column(name = "binary_present", nullable = false)
    private boolean binaryPresent = true;

    /** RN-803: calculado no servidor; o cliente não reimplementa a regra (§23). */
    public boolean isDownloadable() {
        return scanStatus.isDownloadable() && binaryPresent;
    }

    /** §4.9: entrada em {@code CLEAN}. */
    public void markClean(Instant scannedAt) {
        this.scanStatus = ScanStatus.CLEAN;
        this.scannedAt = scannedAt;
        this.scanThreat = null;
    }

    /**
     * §4.9: entrada em {@code INFECTED}.
     *
     * <p>{@code binaryPresent} passa a {@code false} <b>no mesmo método</b> que muda o estado
     * (INV-ATT-06). Separar as duas mudanças permitiria um instante em que o registro está
     * infectado e o binário continua no storage — e é justamente esse instante que CP-08 proíbe.
     */
    public void markInfected(String threat, Instant scannedAt) {
        this.scanStatus = ScanStatus.INFECTED;
        this.scanThreat = threat;
        this.scannedAt = scannedAt;
        this.binaryPresent = false;
    }

    /** §4.9: entrada em {@code FAILED}, consumindo uma das três tentativas. */
    public void markScanFailed(Instant scannedAt) {
        this.scanStatus = ScanStatus.FAILED;
        this.attemptCount = attemptCount + 1;
        this.scannedAt = scannedAt;
    }

    /** §4.9: {@code FAILED → PENDING}, permitido enquanto {@link #hasAttemptsLeft()}. */
    public void markPendingRetry() {
        this.scanStatus = ScanStatus.PENDING;
    }

    /** CP-11: três falhas encerram o assunto; o usuário reenvia o arquivo (§6.3). */
    public boolean hasAttemptsLeft() {
        return attemptCount < 3;
    }

    /** RN-805: o binário foi removido por ser o último referenciador. */
    public void markBinaryRemoved() {
        this.binaryPresent = false;
    }
}
