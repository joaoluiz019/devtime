package com.devtime.attachment.event;

import com.devtime.shared.event.DomainEvent;
import java.util.UUID;

/**
 * Eventos de domínio da feature 015 (spec §15).
 *
 * <p>BR-181: carregam identificadores, nunca entidades. BR-182: publicados após a persistência.
 */
public final class AttachmentEvents {

    private AttachmentEvents() {}

    /**
     * Upload concluído; a verificação precisa ser enfileirada (passo 14 de §6.1).
     *
     * <p>O consumidor é o {@code ScanWorkerJob}, que na prática varre a fila por {@code
     * scanStatus}. O evento existe para que a verificação possa começar sem esperar o próximo ciclo
     * do job — e o job continua sendo a garantia, porque um evento perdido não pode deixar um anexo
     * em {@code PENDING} para sempre.
     */
    public record AttachmentUploadedEvent(
            UUID attachmentId,
            UUID tenantId,
            String contentType,
            long sizeBytes,
            boolean deduplicated)
            implements DomainEvent {}

    /** Verificação concluída; alimenta telemetria (§29). */
    public record AttachmentScannedEvent(
            UUID attachmentId, UUID tenantId, String scanStatus, long durationMillis)
            implements DomainEvent {}

    /**
     * <b>Ameaça detectada</b> (§15, §29).
     *
     * <p>Consumido por {@code 013-notifications} — que notifica quem enviou com severidade {@code
     * CRITICAL} e sem possibilidade de silenciar — e pelo log de segurança. É o evento que §9 de
     * {@code implementation-order.md} identifica como gatilho do risco crítico da feature.
     *
     * @param threat ameaça identificada; nunca o nome do arquivo nem o conteúdo (§19.1)
     */
    public record AttachmentInfectedEvent(
            UUID attachmentId, UUID tenantId, UUID uploadedBy, String threat, String uploadedFromIp)
            implements DomainEvent {}

    /** Exclusão concluída; alimenta telemetria. */
    public record AttachmentDeletedEvent(UUID attachmentId, UUID tenantId, boolean binaryRemoved)
            implements DomainEvent {}
}
