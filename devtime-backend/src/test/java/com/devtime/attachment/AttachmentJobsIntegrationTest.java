package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import com.devtime.shared.tenancy.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Jobs de §22.4 e caminhos de falha da máquina de §4.9 (T-015-24, T-015-27).
 *
 * <p>Os jobs são {@code @Profile("scheduler")} e não existem no contexto de teste (BR-203). São
 * instanciados aqui com os colaboradores reais: o que interessa verificar é o comportamento do job,
 * não o agendamento — e depender do agendador tornaria o teste dependente de tempo (BR-204).
 */
class AttachmentJobsIntegrationTest extends AttachmentTestSupport {

    @Autowired private AttachmentMetrics metrics;
    @Autowired private com.devtime.shared.storage.StoragePort storagePort;
    @Autowired private TenantContext tenantContextBean;

    @Test
    @DisplayName("§22.4: o ScanWorkerJob esvazia a fila de PENDING e define o tenant por item")
    void scanWorkerShouldProcessPendingQueue() {
        UUID ticketId = newTicket();
        AttachmentResponse first = uploadPng(ticketId, "primeiro.png");

        newJobs().processScanQueue();

        assertThat(reload(first.id()).getScanStatus())
                .as("BR-049: o job define o contexto do tenant do próprio anexo a cada item")
                .isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    @DisplayName("CP-11: o job não devolve à fila um anexo com as três tentativas esgotadas")
    void scanWorkerShouldNotRetryExhaustedAttachment() {
        UUID ticketId = newTicket();
        AttachmentResponse exhausted = uploadPng(ticketId, "esgotado.png");
        forceState(exhausted.id(), ScanStatus.FAILED, 3);

        newJobs().processScanQueue();

        Attachment after = reload(exhausted.id());
        assertThat(after.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(after.getAttemptCount()).as("§4.9: a quarta tentativa não existe").isEqualTo(3);
    }

    @Test
    @DisplayName("CP-10/OB-05: o OrphanBinaryJob detecta e alerta, sem remover nada do storage")
    void orphanBinaryJobMustAlertWithoutRemoving() {
        UUID ticketId = newTicket();
        AttachmentResponse attachment = uploadPng(ticketId, "referenciado.png");
        String referencedKey = storageKeyOf(attachment.id());
        // Binário sem registro que o referencie — o órfão que o job deve apontar.
        String orphanKey = tenantAId + "/attachments/2026/01/" + "f".repeat(64);
        storagePort.store(
                orphanKey,
                new java.io.ByteArrayInputStream(AttachmentFixtures.png()),
                AttachmentFixtures.png().length,
                "image/png");

        newJobs().detectOrphanBinaries();

        assertThat(storagePort.exists(orphanKey))
                .as(
                        "remover com base numa inferência sobre a contagem de referências é"
                                + " irreversível se a contagem tiver defeito — detectar é do sistema,"
                                + " corrigir é humano")
                .isTrue();
        assertThat(storagePort.exists(referencedKey)).isTrue();
    }

    @Test
    @DisplayName("§4.9/AV-02: binário ausente no storage vira FAILED e nunca CLEAN")
    void missingBinaryMustFailClosed() {
        UUID ticketId = newTicket();
        AttachmentResponse attachment = uploadPng(ticketId, "sumiu.png");
        // Remove o binário por fora, simulando a corrida entre exclusão e verificação.
        storagePort.delete(storageKeyOf(attachment.id()));

        ScanService.ScanOutcome outcome = asOwnerOfA(() -> scanService.scan(attachment.id()));

        assertThat(outcome).isEqualTo(ScanService.ScanOutcome.FAILED);
        Attachment after = reload(attachment.id());
        assertThat(after.getScanStatus())
                .as("AV-02: nada além de um veredito explícito de ausência de ameaça libera")
                .isEqualTo(ScanStatus.FAILED);
        assertThat(after.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-185: verificar um anexo inexistente é convergente e não lança")
    void scanningUnknownAttachmentIsSkipped() {
        assertThat(asOwnerOfA(() -> scanService.scan(UUID.randomUUID())))
                .isEqualTo(ScanService.ScanOutcome.SKIPPED);
    }

    @Test
    @DisplayName("BR-185: anexo excluído não é reprocessado pelo verificador")
    void deletedAttachmentIsSkipped() {
        UUID ticketId = newTicket();
        AttachmentResponse attachment = uploadPng(ticketId, "excluido.png");
        asOwnerOfA(
                () -> {
                    attachmentService.delete(attachment.id());
                    return null;
                });

        assertThat(asOwnerOfA(() -> scanService.scan(attachment.id())))
                .isEqualTo(ScanService.ScanOutcome.SKIPPED);
    }

    private AttachmentJobs newJobs() {
        return new AttachmentJobs(
                attachmentRepository, scanService, storagePort, metrics, tenantContextBean);
    }

    private Attachment reload(UUID attachmentId) {
        return inTransaction(() -> attachmentRepository.findActiveById(attachmentId).orElseThrow());
    }

    private void forceState(UUID attachmentId, ScanStatus status, int attemptCount) {
        asOwnerOfA(
                () ->
                        inTransaction(
                                () -> {
                                    Attachment attachment =
                                            attachmentRepository
                                                    .findActiveById(attachmentId)
                                                    .orElseThrow();
                                    attachment.setScanStatus(status);
                                    attachment.setAttemptCount(attemptCount);
                                    return attachmentRepository.save(attachment);
                                }));
    }
}
