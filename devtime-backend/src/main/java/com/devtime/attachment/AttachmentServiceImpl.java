package com.devtime.attachment;

import com.devtime.attachment.domain.AllowedFileType;
import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.AttachmentTarget;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.attachment.domain.UploadContent;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentListResponse;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import com.devtime.attachment.event.AttachmentEvents.AttachmentDeletedEvent;
import com.devtime.attachment.event.AttachmentEvents.AttachmentUploadedEvent;
import com.devtime.audit.AuditService;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.OwnershipViolationException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.user.UserService;
import com.devtime.user.dto.UserSummary;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de anexo (spec 015 §6).
 *
 * <p><b>A ordem de {@link #upload} segue exatamente a §6.1 e é normativa</b> (BR-062, CE-01). Duas
 * decisões dentro dela são fáceis de inverter e caras (T-015-13):
 *
 * <ul>
 *   <li>validar o <b>tamanho antes</b> de qualquer leitura de conteúdo (CE-02, CA-03), o que evita
 *       exaustão de recursos com um arquivo que será descartado de todo modo;
 *   <li>gravar o binário <b>depois</b> da validação de assinatura (CP-04), o que evita colocar
 *       conteúdo não verificado no storage.
 * </ul>
 *
 * <p>§28 e CP-19: <b>nem {@code fileName} nem {@code originalFileName} entram em log</b>. São texto
 * livre e podem conter dado pessoal — {@code contrato-joao-silva.pdf} é um exemplo trivial. A
 * auditoria, que é dado do tenant e não log de aplicação, registra o nome (§18).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private static final String ENTITY_TYPE = "Attachment";

    private final AttachmentRepository repository;
    private final AttachmentMapper mapper;
    private final TargetExclusivityValidator targetValidator;
    private final AttachmentLimitPolicy limitPolicy;
    private final UploadValidator uploadValidator;
    private final QuotaService quotaService;
    private final MagicNumberValidator magicNumberValidator;
    private final FileNameSanitizer fileNameSanitizer;
    private final ChecksumCalculator checksumCalculator;
    private final DeduplicationPolicy deduplicationPolicy;
    private final AttachmentMetrics metrics;
    private final UserService userService;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'ATTACHMENT_VIEW')")
    public AttachmentListResponse listByTicket(UUID ticketId) {
        targetValidator.assertTargetExists(AttachmentTarget.ticket(ticketId));
        return toListResponse(repository.findByTicket(ticketId), AttachmentTarget.Kind.TICKET);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'ATTACHMENT_VIEW')")
    public AttachmentListResponse listByComment(UUID commentId) {
        targetValidator.assertTargetExists(AttachmentTarget.comment(commentId));
        return toListResponse(repository.findByComment(commentId), AttachmentTarget.Kind.COMMENT);
    }

    /**
     * Ordem normativa de §6.1. O passo 1 (permissão) é o {@code @PreAuthorize}; os demais estão
     * numerados no corpo.
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'ATTACHMENT_UPLOAD')")
    public AttachmentResponse upload(
            UUID ticketId, UUID commentId, UploadContent content, String uploadedFromIp) {

        // 2 — INV-ATT-01: exatamente um alvo.
        AttachmentTarget target = targetValidator.requireSingleTarget(ticketId, commentId);
        // 3 — alvo existe no tenant; de outro tenant responde 404 (ART-024).
        targetValidator.assertTargetExists(target);
        // 4 — RN-806: limite de anexos no alvo.
        limitPolicy.assertBelowLimit(target);
        // 5 — RN-801: tamanho. ANTES de qualquer leitura de conteúdo (CE-02).
        uploadValidator.assertSizeWithinLimit(content);
        // 6 — RN-801: quota do tenant.
        quotaService.assertFits(content.sizeBytes());
        // 7 e 8 — RN-802: allowlist e assinatura binária, nesta ordem, dentro do validador.
        AllowedFileType type = magicNumberValidator.assertDeclaredTypeMatchesContent(content);
        // 9 — RN-804: sanitizar o nome; o original vira metadado.
        String fileName = fileNameSanitizer.sanitize(content.originalFileName());
        // 10 — checksum em fluxo (CP-14).
        String checksum = checksumCalculator.sha256(content);
        // 11 e 12 — RN-805: reusa a storageKey ou grava o binário. Só aqui o conteúdo chega ao
        // storage, e apenas depois de o passo 8 ter passado (CP-04).
        var deduplication = deduplicationPolicy.resolve(checksum, content, type.contentType());

        // 13 — persiste em PENDING; o download nasce bloqueado (RN-803).
        Attachment attachment = new Attachment();
        attachment.setTicketId(target.ticketId());
        attachment.setCommentId(target.commentId());
        attachment.setFileName(fileName);
        attachment.setOriginalFileName(
                fileNameSanitizer.truncateOriginal(content.originalFileName()));
        attachment.setContentType(type.contentType());
        attachment.setSizeBytes(content.sizeBytes());
        attachment.setStorageKey(deduplication.storageKey());
        attachment.setChecksumSha256(checksum);
        attachment.setScanStatus(ScanStatus.PENDING);
        attachment.setUploadedFromIp(uploadedFromIp);
        attachment.setUploadedBy(tenantContext.requireUserId()); // BR-041: nunca da requisição
        Attachment saved = repository.save(attachment);

        // RN-006: trilha na mesma transação. O nome entra aqui, e só aqui (§18).
        auditService.record(
                "ATTACHMENT_UPLOADED",
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                Map.of(
                        "fileName", fileName,
                        "contentType", type.contentType(),
                        "sizeBytes", saved.getSizeBytes(),
                        "checksum", checksum,
                        "deduplicated", deduplication.deduplicated()));

        // 14 — enfileira a verificação. Publicado após a persistência (BR-182).
        events.publish(
                new AttachmentUploadedEvent(
                        saved.getId(),
                        saved.getTenantId(),
                        type.contentType(),
                        saved.getSizeBytes(),
                        deduplication.deduplicated()));

        metrics.uploaded(type.contentType(), deduplication.deduplicated());
        // §28: nome ausente do log, por CP-19.
        log.info(
                "upload aceito attachmentId={} contentType={} sizeBytes={} deduplicado={}",
                saved.getId(),
                type.contentType(),
                saved.getSizeBytes(),
                deduplication.deduplicated());

        // 15 — 201 Created; download BLOQUEADO até a verificação concluir.
        return toResponse(saved, resolvePeople(List.of(saved)));
    }

    @Override
    @Transactional
    @PreAuthorize(
            "hasPermission(null, 'ATTACHMENT_DELETE_OWN')"
                    + " or hasPermission(null, 'ATTACHMENT_DELETE_ANY')")
    public void delete(UUID attachmentId) {
        Attachment attachment = require(attachmentId);
        assertCanDelete(attachment); // OWN-07

        // RN-805, passos 5 e 6: o binário sai apenas se este for o último referenciador.
        boolean binaryRemoved = deduplicationPolicy.removeBinaryIfLastReference(attachment);
        if (binaryRemoved) {
            attachment.markBinaryRemoved();
        }
        // RN-003: exclusão lógica do registro.
        repository.softDelete(
                attachmentId, clock.now(), tenantContext.currentUserId().orElse(null));

        auditService.record(
                "ATTACHMENT_DELETED",
                ENTITY_TYPE,
                attachmentId,
                Map.of(
                        "fileName", attachment.getFileName(),
                        "scanStatus", attachment.getScanStatus().name()),
                Map.of("deletedAt", clock.now().toString()),
                Map.of("binaryRemoved", binaryRemoved));

        events.publish(
                new AttachmentDeletedEvent(attachmentId, attachment.getTenantId(), binaryRemoved));
        log.info("anexo excluído attachmentId={} binarioRemovido={}", attachmentId, binaryRemoved);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private Attachment require(UUID id) {
        // ART-024: inexistente e de outro tenant produzem a mesma resposta (SG-06, SG-15).
        return repository
                .findById(id)
                .orElseThrow(() -> EntityNotFoundException.of(Attachment.class, id));
    }

    /**
     * OWN-07: o anexo pertence a {@code uploadedBy}.
     *
     * <p>Diferente de comentários, <b>não</b> há janela temporal — o autor exclui a qualquer
     * momento (§16). A verificação de ownership ocorre <b>depois</b> da de permissão e da de tenant
     * (AZ-03, §4.1 de permissions.md), que é por que ela vive aqui e não no {@code @PreAuthorize}.
     */
    private void assertCanDelete(Attachment attachment) {
        if (!canDelete(attachment)) {
            throw new OwnershipViolationException(ENTITY_TYPE);
        }
    }

    private boolean canDelete(Attachment attachment) {
        if (hasPermission(Permission.ATTACHMENT_DELETE_ANY)) {
            return true;
        }
        return hasPermission(Permission.ATTACHMENT_DELETE_OWN)
                && tenantContext.requireUserId().equals(attachment.getUploadedBy());
    }

    private boolean hasPermission(Permission permission) {
        return tenantContext.session().map(s -> s.permissions().contains(permission)).orElse(false);
    }

    private AttachmentListResponse toListResponse(
            List<Attachment> attachments, AttachmentTarget.Kind kind) {
        Map<UUID, UserSummary> people = resolvePeople(attachments);
        List<AttachmentResponse> content =
                attachments.stream().map(item -> toResponse(item, people)).toList();
        return new AttachmentListResponse(content, content.size(), limitPolicy.maxFor(kind));
    }

    /** Quem enviou, de toda a lista, em <b>uma</b> consulta — evita N+1 (§20). */
    private Map<UUID, UserSummary> resolvePeople(List<Attachment> attachments) {
        Set<UUID> ids = new LinkedHashSet<>();
        attachments.forEach(attachment -> ids.add(attachment.getUploadedBy()));
        Map<UUID, UserSummary> resolved = new HashMap<>(userService.findSummaries(ids));
        // RN-458: quem saiu continua vinculado; apenas o nome exibido muda (§19.1).
        ids.forEach(id -> resolved.computeIfAbsent(id, UserSummary::removed));
        return resolved;
    }

    private AttachmentResponse toResponse(Attachment attachment, Map<UUID, UserSummary> people) {
        return mapper.toResponse(
                attachment,
                people.getOrDefault(
                        attachment.getUploadedBy(),
                        UserSummary.removed(attachment.getUploadedBy())),
                canDelete(attachment));
    }
}
