package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import com.devtime.shared.error.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Verificação antivírus e máquina de §4.9 (T-015-24, T-015-27).
 *
 * <p><b>O teste com EICAR é o gatilho de acionamento do risco crítico da feature</b> (§9 de {@code
 * implementation-order.md}, DoD-02). Sem ele, a proteção contra arquivo malicioso é uma suposição.
 * Ele roda contra um ClamAV real: um dublê programado para responder {@code INFECTED} provaria
 * apenas que o dublê responde o que foi programado para responder.
 */
class AttachmentScanIntegrationTest extends AttachmentTestSupport {

    // ── DoD-02 / CA-08 / CA-09 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CA-08/SG-02: EICAR é detectado, vira INFECTED e o binário sai do storage")
    void eicarMustBeDetectedAndBinaryRemoved() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded =
                asOwnerOfA(
                        () ->
                                attachmentService.upload(
                                        ticketId,
                                        null,
                                        AttachmentFixtures.upload(
                                                "amostra.txt",
                                                "text/plain",
                                                AttachmentFixtures.eicar()),
                                        "203.0.113.10"));
        String storageKey = storageKeyOf(uploaded.id());
        assertThat(storage.exists(storageKey)).isTrue();

        ScanService.ScanOutcome outcome = asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(outcome).isEqualTo(ScanService.ScanOutcome.INFECTED);
        Attachment scanned = reload(uploaded.id());
        assertThat(scanned.getScanStatus()).isEqualTo(ScanStatus.INFECTED);
        assertThat(scanned.getScanThreat())
                .as("§18: a ameaça identificada é a base de qualquer investigação de segurança")
                .isNotBlank();
        assertThat(storage.exists(storageKey))
                .as(
                        "INV-ATT-06/CP-08: a remoção é efeito de entrada do estado, não passo posterior")
                .isFalse();
    }

    @Test
    @DisplayName("CA-09/SG-03: EICAR dentro de ZIP também é detectado")
    void eicarInsideZipMustBeDetected() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded =
                asOwnerOfA(
                        () ->
                                attachmentService.upload(
                                        ticketId,
                                        null,
                                        AttachmentFixtures.upload(
                                                "amostra.zip",
                                                "application/zip",
                                                AttachmentFixtures.eicarInZip()),
                                        "203.0.113.10"));
        String storageKey = storageKeyOf(uploaded.id());

        asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(reload(uploaded.id()).getScanStatus())
                .as("CX-06: o ZIP passa pela allowlist e é barrado pelo antivírus — RS-03")
                .isEqualTo(ScanStatus.INFECTED);
        assertThat(storage.exists(storageKey)).isFalse();
    }

    @Test
    @DisplayName("FA-04/CA-10: download de arquivo INFECTED responde 403 DEVTIME-2703")
    void downloadOfInfectedMustBeForbidden() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded =
                asOwnerOfA(
                        () ->
                                attachmentService.upload(
                                        ticketId,
                                        null,
                                        AttachmentFixtures.upload(
                                                "amostra.txt",
                                                "text/plain",
                                                AttachmentFixtures.eicar()),
                                        null));
        asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThatThrownBy(() -> asOwnerOfA(() -> downloadService.download(uploaded.id(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure -> {
                            var business = (BusinessRuleException) failure;
                            assertThat(business.getErrorCode().getCode()).isEqualTo("DEVTIME-2703");
                            assertThat(business.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        });
    }

    // ── Fluxo limpo ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-803/CA-23: após CLEAN o download responde URL assinada e é auditado")
    void cleanAttachmentShouldBeDownloadable() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded = uploadPng(ticketId, "captura.png");

        ScanService.ScanOutcome outcome = asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(outcome).isEqualTo(ScanService.ScanOutcome.CLEAN);
        var download = asOwnerOfA(() -> downloadService.download(uploaded.id(), "203.0.113.10"));
        assertThat(download.url())
                .as("CP-15/OB-06: o binário não passa pela aplicação — a resposta é uma URL")
                .contains(storageKeyOf(uploaded.id()));
        assertThat(download.expiresAt())
                .as("SG-08: validade curta; uma URL longeva é um link público com data marcada")
                .isAfter(NOW);
    }

    @Test
    @DisplayName("§23: canDownload passa a verdadeiro apenas depois de CLEAN")
    void canDownloadIsComputedByServerFromScanStatus() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded = uploadPng(ticketId, "captura.png");
        assertThat(uploaded.canDownload()).isFalse();

        asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(
                        asOwnerOfA(() -> attachmentService.listByTicket(ticketId))
                                .attachments()
                                .stream()
                                .filter(item -> item.id().equals(uploaded.id()))
                                .findFirst()
                                .orElseThrow()
                                .canDownload())
                .isTrue();
    }

    // ── Estados bloqueados ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FA-03/CA-10: download em PENDING responde 409 DEVTIME-2703")
    void downloadWhilePendingMustConflict() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded = uploadPng(ticketId, "em-verificacao.png");

        assertThatThrownBy(() -> asOwnerOfA(() -> downloadService.download(uploaded.id(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure -> {
                            var business = (BusinessRuleException) failure;
                            assertThat(business.getErrorCode().getCode()).isEqualTo("DEVTIME-2703");
                            assertThat(business.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(business.getDetails())
                                    .as(
                                            "CP-20: a UI precisa distinguir 'aguarde' de 'foi bloqueado'")
                                    .containsEntry("scanStatus", ScanStatus.PENDING.name());
                        });
    }

    @Test
    @DisplayName("FA-05/CA-10: download em FAILED responde 409 e nunca libera")
    void downloadAfterFailedScanMustConflict() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded = uploadPng(ticketId, "falhou.png");
        forceState(uploaded.id(), ScanStatus.FAILED, 3);

        assertThatThrownBy(() -> asOwnerOfA(() -> downloadService.download(uploaded.id(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure -> {
                            var business = (BusinessRuleException) failure;
                            assertThat(business.getErrorCode().getCode()).isEqualTo("DEVTIME-2703");
                            assertThat(business.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        });
    }

    @Test
    @DisplayName("CA-20/CP-11: três tentativas esgotadas não produzem uma quarta")
    void exhaustedAttemptsMustNotBeRetried() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded = uploadPng(ticketId, "esgotado.png");
        forceState(uploaded.id(), ScanStatus.FAILED, 3);

        ScanService.ScanOutcome outcome = asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(outcome).isEqualTo(ScanService.ScanOutcome.EXHAUSTED);
        Attachment after = reload(uploaded.id());
        assertThat(after.getAttemptCount())
                .as("§4.9: três falhas indicam problema que nova tentativa não resolve")
                .isEqualTo(3);
        assertThat(after.getScanStatus()).isEqualTo(ScanStatus.FAILED);
    }

    @Test
    @DisplayName("BR-185: reprocessar um anexo já CLEAN é convergente e não altera o estado")
    void rescanningCleanAttachmentIsIdempotent() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded = uploadPng(ticketId, "captura.png");
        asOwnerOfA(() -> scanService.scan(uploaded.id()));

        ScanService.ScanOutcome second = asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(second).isEqualTo(ScanService.ScanOutcome.SKIPPED);
        assertThat(reload(uploaded.id()).getScanStatus()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    @DisplayName("§11.1: INFECTED é terminal — reprocessar não reabre a verificação")
    void infectedIsTerminal() {
        UUID ticketId = newTicket();
        AttachmentResponse uploaded =
                asOwnerOfA(
                        () ->
                                attachmentService.upload(
                                        ticketId,
                                        null,
                                        AttachmentFixtures.upload(
                                                "amostra.txt",
                                                "text/plain",
                                                AttachmentFixtures.eicar()),
                                        null));
        asOwnerOfA(() -> scanService.scan(uploaded.id()));

        assertThat(asOwnerOfA(() -> scanService.scan(uploaded.id())))
                .as("o binário já foi removido; não há o que verificar novamente")
                .isEqualTo(ScanService.ScanOutcome.SKIPPED);
        assertThat(reload(uploaded.id()).getScanStatus()).isEqualTo(ScanStatus.INFECTED);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private Attachment reload(UUID attachmentId) {
        return inTransaction(() -> attachmentRepository.findActiveById(attachmentId).orElseThrow());
    }

    /**
     * Leva o anexo a um estado da máquina de §4.9 sem depender do verificador.
     *
     * <p>Escreve pela entidade, e não por SQL bruto (BR-207). Os estados de falha exigiriam
     * derrubar o ClamAV no meio da suíte para serem alcançados pelo caminho natural — o que
     * tornaria o teste dependente da ordem de execução (BR-204).
     */
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
