package com.devtime.attachment;

import com.devtime.attachment.domain.UploadContent;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.storage.StoragePort;
import com.devtime.support.AttachmentInfrastructureConfiguration;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Base dos testes de integração de {@code 015-attachments}.
 *
 * <p>Acrescenta MinIO e ClamAV reais ao cenário de dois tenants de {@link FeatureTestSupport} — o
 * que torna verificáveis os dois critérios que nenhum dublê alcança: detecção real do EICAR
 * (DoD-02) e remoção do binário comprovada no storage (DoD-06).
 */
@Import(AttachmentInfrastructureConfiguration.class)
abstract class AttachmentTestSupport extends FeatureTestSupport {

    @Autowired protected AttachmentService attachmentService;
    @Autowired protected AttachmentDownloadService downloadService;
    @Autowired protected ScanService scanService;
    @Autowired protected QuotaService quotaService;
    @Autowired protected AttachmentRepository attachmentRepository;
    @Autowired protected StoragePort storage;
    @Autowired protected TicketScenario scenario;
    @Autowired protected TicketService ticketService;

    /** Ticket ativo do tenant A, alvo padrão dos anexos. */
    protected UUID newTicket() {
        ContractResponse contract =
                asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
        return asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Ticket com anexos",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))
                .id();
    }

    protected AttachmentResponse uploadPng(UUID ticketId, String fileName) {
        return asOwnerOfA(
                () -> attachmentService.upload(ticketId, null, png(fileName), "203.0.113.10"));
    }

    protected UploadContent png(String fileName) {
        return AttachmentFixtures.upload(fileName, "image/png", AttachmentFixtures.png());
    }

    /**
     * Chave de storage do anexo, obtida diretamente da entidade.
     *
     * <p>Necessário porque {@code storageKey} nunca é exposta em resposta (CP-07, CA-21) — e é
     * justamente por isso que os testes que a usam provam DoD-06 por acesso direto ao storage, e
     * não por um campo que a API entregaria.
     */
    protected String storageKeyOf(UUID attachmentId) {
        return inTransaction(
                () ->
                        attachmentRepository
                                .findActiveById(attachmentId)
                                .orElseThrow()
                                .getStorageKey());
    }
}
