package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.attachment.event.AttachmentEvents.AttachmentInfectedEvent;
import com.devtime.attachment.event.AttachmentEvents.AttachmentScannedEvent;
import com.devtime.audit.AuditService;
import com.devtime.shared.antivirus.AntivirusPort;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.storage.StoragePort;
import com.devtime.shared.time.TenantClock;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Máquina de verificação de §4.9 (T-015-14).
 *
 * <p>Três transições a partir de {@code PENDING}, e uma de volta:
 *
 * <ul>
 *   <li>{@code CLEAN} — download liberado (RN-803);
 *   <li>{@code INFECTED} — <b>binário removido como efeito de entrada</b> (INV-ATT-06, CP-08), quem
 *       enviou é notificado e o evento de segurança é registrado;
 *   <li>{@code FAILED} — reprocessa até 3 vezes; depois permanece {@code FAILED} e o download
 *       continua bloqueado, <b>permanentemente</b> (§6.3, CP-11).
 * </ul>
 *
 * <p>AV-02 é o princípio que organiza tudo aqui: falha ou indisponibilidade do verificador
 * <b>nunca</b> libera o arquivo. Por isso o único caminho que chega a {@code CLEAN} é um veredito
 * explícito de ausência de ameaça — tudo o mais, inclusive o inesperado, termina bloqueado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ScanServiceImpl implements ScanService {

    private static final String ENTITY_TYPE = "Attachment";

    private final AttachmentRepository repository;
    private final AntivirusPort antivirus;
    private final StoragePort storage;
    private final AttachmentMetrics metrics;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantClock clock;

    @Override
    @Transactional
    public ScanOutcome scan(UUID attachmentId) {
        Optional<Attachment> found = repository.findActiveById(attachmentId);
        if (found.isEmpty()) {
            return ScanOutcome.SKIPPED;
        }
        Attachment attachment = found.get();

        // BR-185: convergente. Um anexo já resolvido, excluído ou sem binário não é reprocessado —
        // o job pode reexecutar sobre a mesma linha sem alterar o resultado.
        if (attachment.isDeleted()
                || !attachment.isBinaryPresent()
                || attachment.getScanStatus() == ScanStatus.CLEAN
                || attachment.getScanStatus() == ScanStatus.INFECTED) {
            return ScanOutcome.SKIPPED;
        }
        // CP-11: a quarta tentativa não existe.
        if (!attachment.hasAttemptsLeft()) {
            return ScanOutcome.EXHAUSTED;
        }

        Instant startedAt = clock.now();
        AntivirusPort.ScanResult result = verify(attachment);
        Duration duration = Duration.between(startedAt, clock.now());
        metrics.scanDuration(duration, attachment.getSizeBytes());

        return switch (result.verdict()) {
            case CLEAN -> applyClean(attachment, duration);
            case INFECTED -> applyInfected(attachment, result.threat());
            case FAILED -> applyFailed(attachment, result.failureReason());
        };
    }

    /**
     * Lê o binário e submete ao verificador.
     *
     * <p>BR-070/BR-127 proíbem chamada externa dentro de transação, e esta é uma. A exceção é
     * consciente e limitada: a transição de estado precisa ser atômica com a remoção do binário
     * (INV-ATT-06) e com a trilha (RN-006), e separar a chamada da escrita abriria uma janela em
     * que o veredito existe e o estado ainda não — janela em que um download de arquivo infectado
     * seria liberado. A transação é curta porque o job processa um anexo por vez, e o {@code
     * readTimeout} do adapter limita sua duração (BR-126).
     */
    private AntivirusPort.ScanResult verify(Attachment attachment) {
        Optional<InputStream> content = storage.openStream(attachment.getStorageKey());
        if (content.isEmpty()) {
            // O binário sumiu entre o enfileiramento e o processamento — exclusão do último
            // referenciador, por exemplo. Não é falha do verificador.
            return AntivirusPort.ScanResult.failed("binário ausente no storage");
        }
        try (InputStream in = content.get()) {
            return antivirus.scan(in);
        } catch (IOException unreadable) {
            return AntivirusPort.ScanResult.failed("conteúdo ilegível: " + unreadable.getMessage());
        }
    }

    /** §4.9: {@code PENDING → CLEAN}. Único caminho que libera o download. */
    private ScanOutcome applyClean(Attachment attachment, Duration duration) {
        attachment.markClean(clock.now());
        auditService.recordSystemAction(
                "ATTACHMENT_SCAN_CLEAN",
                ENTITY_TYPE,
                attachment.getId(),
                Map.of("scanStatus", ScanStatus.PENDING.name()),
                Map.of("scanStatus", ScanStatus.CLEAN.name()),
                Map.of("durationMillis", duration.toMillis()));
        publishScanned(attachment, duration);
        log.info(
                "anexo verificado sem ameaça attachmentId={} duracaoMs={}",
                attachment.getId(),
                duration.toMillis());
        return ScanOutcome.CLEAN;
    }

    /**
     * §4.9: {@code PENDING → INFECTED}.
     *
     * <p>A remoção do binário é <b>efeito de entrada</b> do estado (INV-ATT-06, CP-08): acontece
     * aqui, não em um passo posterior nem em um job de limpeza. Um arquivo infectado que permanece
     * no storage por qualquer intervalo é um arquivo infectado disponível.
     */
    private ScanOutcome applyInfected(Attachment attachment, String threat) {
        storage.delete(attachment.getStorageKey());
        attachment.markInfected(threat, clock.now());

        // §18: o único registro de uma tentativa de introduzir arquivo malicioso, e a base de
        // qualquer investigação. Registra a ameaça, quem enviou e o IP do upload.
        auditService.recordSystemAction(
                "ATTACHMENT_SCAN_INFECTED",
                ENTITY_TYPE,
                attachment.getId(),
                Map.of("scanStatus", ScanStatus.PENDING.name()),
                Map.of("scanStatus", ScanStatus.INFECTED.name()),
                Map.of(
                        "threat", threat,
                        "uploadedBy", attachment.getUploadedBy(),
                        "uploadedFromIp", nullSafe(attachment.getUploadedFromIp())));

        events.publish(
                new AttachmentInfectedEvent(
                        attachment.getId(),
                        attachment.getTenantId(),
                        attachment.getUploadedBy(),
                        threat,
                        attachment.getUploadedFromIp()));
        metrics.infected();

        // §28: ERROR. É o evento que §9 de implementation-order.md identifica como crítico.
        // Nome do arquivo e conteúdo permanecem fora (CP-19).
        log.error(
                "ameaça detectada em anexo attachmentId={} ameaca={} uploadedBy={} ipUpload={}",
                attachment.getId(),
                threat,
                attachment.getUploadedBy(),
                attachment.getUploadedFromIp());
        return ScanOutcome.INFECTED;
    }

    /** §4.9: {@code PENDING → FAILED}, consumindo uma das três tentativas. */
    private ScanOutcome applyFailed(Attachment attachment, String reason) {
        attachment.markScanFailed(clock.now());
        int attempt = attachment.getAttemptCount();

        auditService.recordSystemAction(
                "ATTACHMENT_SCAN_FAILED",
                ENTITY_TYPE,
                attachment.getId(),
                Map.of("scanStatus", ScanStatus.PENDING.name(), "attemptCount", attempt - 1),
                Map.of("scanStatus", ScanStatus.FAILED.name(), "attemptCount", attempt),
                Map.of("cause", nullSafe(reason)));
        metrics.scanFailed(attempt);

        if (attachment.hasAttemptsLeft()) {
            log.warn(
                    "falha na verificação de anexo attachmentId={} tentativa={} causa={}",
                    attachment.getId(),
                    attempt,
                    reason);
            return ScanOutcome.FAILED;
        }
        metrics.scanExhausted();
        // §28: ERROR. A consequência é definitiva — o arquivo fica inacessível para sempre e não
        // existe liberação manual (§6.3, CP-02). O usuário reenvia.
        log.error(
                "verificação esgotada após 3 tentativas attachmentId={} — arquivo permanecerá"
                        + " inacessível",
                attachment.getId());
        return ScanOutcome.EXHAUSTED;
    }

    private void publishScanned(Attachment attachment, Duration duration) {
        events.publish(
                new AttachmentScannedEvent(
                        attachment.getId(),
                        attachment.getTenantId(),
                        attachment.getScanStatus().name(),
                        duration.toMillis()));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
