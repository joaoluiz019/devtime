package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.dto.AttachmentResponses.DownloadResponse;
import com.devtime.audit.AuditService;
import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.storage.StoragePort;
import com.devtime.shared.time.TenantClock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link AttachmentDownloadService} (T-015-15).
 *
 * <p>O {@link DownloadGuard} é aplicado <b>aqui</b>, no serviço, e não apenas no controller
 * (BR-161): uma verificação só na fronteira HTTP seria contornada por qualquer caminho interno que
 * viesse a existir depois.
 *
 * <p>§18: <b>todo download é auditado</b>, como em {@code 012-reports} e pelo mesmo motivo — é o
 * momento em que conteúdo binário sai do sistema, e §19.1 registra que essa trilha é a única forma
 * de responder quem acessou o quê.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AttachmentDownloadServiceImpl implements AttachmentDownloadService {

    private static final String ENTITY_TYPE = "Attachment";

    private final AttachmentRepository repository;
    private final DownloadGuard downloadGuard;
    private final StoragePort storage;
    private final AttachmentMetrics metrics;
    private final AuditService auditService;
    private final DevTimeProperties properties;
    private final TenantClock clock;

    /**
     * A auditoria exige escrita, então o método é transacional apesar de conceitualmente ser
     * leitura. É deliberado: um download não auditado é pior que um download recusado, porque a
     * ausência do registro só aparece quando alguém precisa dele.
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'ATTACHMENT_VIEW')")
    public DownloadResponse download(UUID attachmentId, String requestIp) {
        Attachment attachment =
                repository
                        .findById(attachmentId)
                        // ART-024 / SG-15: de outro tenant é indistinguível de inexistente.
                        .orElseThrow(
                                () -> EntityNotFoundException.of(Attachment.class, attachmentId));

        try {
            downloadGuard.assertDownloadable(attachment); // RN-803, INV-ATT-02
        } catch (BusinessRuleException blocked) {
            metrics.downloadBlocked(attachment.getScanStatus().name());
            throw blocked;
        }

        Duration ttl = properties.storage().downloadUrlTtl();
        String url =
                storage.presignedDownloadUrl(
                        attachment.getStorageKey(), ttl, attachment.getFileName());

        // §18: quem baixou, IP e traceId. O nome do arquivo permanece fora do log (CP-19), mas
        // entra na trilha, que é dado do tenant.
        auditService.record(
                "ATTACHMENT_DOWNLOADED",
                ENTITY_TYPE,
                attachmentId,
                Map.of(),
                Map.of(),
                Map.of("requestIp", requestIp == null ? "" : requestIp));

        metrics.downloaded();
        log.info("download de anexo attachmentId={} ip={}", attachmentId, requestIp);

        return new DownloadResponse(url, clock.now().plus(ttl));
    }
}
