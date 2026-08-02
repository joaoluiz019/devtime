package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.attachment.domain.ScanStatus;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import com.devtime.comment.CommentService;
import com.devtime.comment.dto.CommentRequests.CommentCreateRequest;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Upload, limites, quota e deduplicação (T-015-26, T-015-28, T-015-29).
 *
 * <p>Cobre a ordem normativa de §6.1, RN-801, RN-805, RN-806, INV-ATT-01 e o isolamento entre
 * tenants.
 */
class AttachmentServiceIntegrationTest extends AttachmentTestSupport {

    @Autowired private CommentService commentService;

    // ── Ordem de §6.1 e estado inicial ───────────────────────────────────────────────────────

    @Test
    @DisplayName("§6.1 passo 13/15: o anexo nasce em PENDING e o download nasce bloqueado")
    void uploadShouldStartPendingWithDownloadBlocked() {
        UUID ticketId = newTicket();

        AttachmentResponse created = uploadPng(ticketId, "captura.png");

        assertThat(created.scanStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(created.canDownload())
                .as("RN-803: 201 Created não libera download — a verificação ainda não ocorreu")
                .isFalse();
    }

    @Test
    @DisplayName("CA-21/CP-07: storageKey e checksum estão ausentes de toda resposta")
    void responseMustNotExposeStorageKeyOrChecksum() {
        UUID ticketId = newTicket();
        AttachmentResponse created = uploadPng(ticketId, "captura.png");

        // A prova é estrutural: o record não possui os campos. Um teste sobre o JSON serializado
        // passaria a falhar apenas depois de o campo já ter sido publicado.
        assertThat(AttachmentResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("storageKey", "checksumSha256", "checksum");
        assertThat(created.id()).isNotNull();
    }

    @Test
    @DisplayName("§6.1 passo 5/CA-02: 10 MB é aceito e 10 MB + 1 byte é rejeitado com DEVTIME-2701")
    void shouldEnforceFileSizeBoundary() {
        UUID ticketId = newTicket();
        int tenMegabytes = 10 * 1024 * 1024;

        assertThatCode(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId,
                                                        null,
                                                        AttachmentFixtures.upload(
                                                                "limite.png",
                                                                "image/png",
                                                                AttachmentFixtures.pngOfSize(
                                                                        tenMegabytes)),
                                                        null)))
                .doesNotThrowAnyException();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId,
                                                        null,
                                                        AttachmentFixtures.upload(
                                                                "acima.png",
                                                                "image/png",
                                                                AttachmentFixtures.pngOfSize(
                                                                        tenMegabytes + 1)),
                                                        null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2701");
    }

    @Test
    @DisplayName("CX-02: arquivo de 0 byte é rejeitado antes de qualquer leitura de conteúdo")
    void shouldRejectEmptyFile() {
        UUID ticketId = newTicket();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId,
                                                        null,
                                                        AttachmentFixtures.upload(
                                                                "vazio.png",
                                                                "image/png",
                                                                AttachmentFixtures.empty()),
                                                        null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2701");
    }

    @Test
    @DisplayName("CA-03/CE-02: o tamanho é rejeitado antes de o conteúdo ser lido")
    void sizeMustBeRejectedBeforeReadingContent() {
        UUID ticketId = newTicket();
        CountingContent oversized =
                new CountingContent(
                        AttachmentFixtures.upload(
                                "grande.png",
                                "image/png",
                                AttachmentFixtures.pngOfSize(10 * 1024 * 1024 + 1)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId, null, oversized, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(oversized.streamsOpened())
                .as(
                        "§6.1: ler os primeiros bytes de um upload que será descartado abre caminho"
                                + " para exaustão de recursos (SG-11)")
                .isZero();
    }

    @Test
    @DisplayName("CX-03/FA-06: executável renomeado para .pdf é rejeitado com DEVTIME-2702")
    void shouldRejectRenamedExecutable() {
        UUID ticketId = newTicket();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId,
                                                        null,
                                                        AttachmentFixtures.upload(
                                                                "documento.pdf",
                                                                "application/pdf",
                                                                AttachmentFixtures
                                                                        .windowsExecutable()),
                                                        null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2702");
    }

    @Test
    @DisplayName("CP-04: o binário não chega ao storage quando a assinatura é rejeitada")
    void binaryMustNotReachStorageWhenSignatureFails() {
        UUID ticketId = newTicket();
        long before = asOwnerOfA(() -> quotaService.current()).usedBytes();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId,
                                                        null,
                                                        AttachmentFixtures.upload(
                                                                "documento.pdf",
                                                                "application/pdf",
                                                                AttachmentFixtures
                                                                        .windowsExecutable()),
                                                        null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(asOwnerOfA(() -> quotaService.current()).usedBytes())
                .as("nenhum registro criado implica nenhum binário gravado")
                .isEqualTo(before);
    }

    // ── RN-804 e SG-05 ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CX-08/RN-804: nome com path traversal é sanitizado e o original é preservado")
    void shouldSanitizeNameAndKeepOriginal() {
        UUID ticketId = newTicket();

        AttachmentResponse created = uploadPng(ticketId, "../../etc/passwd.png");

        assertThat(created.fileName()).isEqualTo("passwd.png");
        assertThat(created.originalFileName()).isEqualTo("../../etc/passwd.png");
    }

    @Test
    @DisplayName("CA-13/CP-05: a storageKey não contém nenhuma parte do nome do arquivo")
    void storageKeyMustBeOpaque() {
        UUID ticketId = newTicket();
        AttachmentResponse created = uploadPng(ticketId, "relatorio-confidencial.png");

        String storageKey = storageKeyOf(created.id());

        assertThat(storageKey).doesNotContain("relatorio", "confidencial", ".png");
        assertThat(storageKey)
                .as("integrations.md §6.2: {tenantId}/attachments/{yyyy}/{MM}/{checksum}")
                .startsWith(tenantAId + "/attachments/")
                .matches(".*/[0-9a-f]{64}$");
    }

    // ── RN-806 ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-806/FA-10: o 21º anexo do ticket é rejeitado com DEVTIME-2704")
    void shouldRejectTwentyFirstTicketAttachment() {
        UUID ticketId = newTicket();
        for (int index = 0; index < 20; index++) {
            uploadPng(ticketId, "anexo-" + index + ".png");
        }

        assertThatThrownBy(() -> uploadPng(ticketId, "anexo-21.png"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2704");
    }

    @Test
    @DisplayName("RN-806/CX-19: os limites de ticket e comentário são independentes")
    void ticketAndCommentLimitsAreIndependent() {
        UUID ticketId = newTicket();
        UUID commentId = newComment(ticketId);
        for (int index = 0; index < 20; index++) {
            uploadPng(ticketId, "ticket-" + index + ".png");
        }

        // O ticket está no limite; o comentário dele continua aceitando os 5 próprios.
        for (int index = 0; index < 5; index++) {
            int current = index;
            assertThatCode(() -> uploadToComment(commentId, "comentario-" + current + ".png"))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> uploadToComment(commentId, "comentario-6.png"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2704");
    }

    @Test
    @DisplayName("§23: maxCount informa o limite do alvo para a UI desabilitar o envio")
    void listShouldExposeTargetLimit() {
        UUID ticketId = newTicket();
        UUID commentId = newComment(ticketId);
        uploadPng(ticketId, "unico.png");

        assertThat(asOwnerOfA(() -> attachmentService.listByTicket(ticketId)).maxCount())
                .isEqualTo(20);
        assertThat(asOwnerOfA(() -> attachmentService.listByComment(commentId)).maxCount())
                .isEqualTo(5);
    }

    // ── INV-ATT-01 ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("INV-ATT-01/FA-17: upload sem alvo ou com dois alvos é rejeitado")
    void shouldRequireExactlyOneTarget() {
        UUID ticketId = newTicket();
        UUID commentId = newComment(ticketId);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        null, null, png("sem-alvo.png"), null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                attachmentService.upload(
                                                        ticketId,
                                                        commentId,
                                                        png("dois-alvos.png"),
                                                        null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── RN-805 ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-805/CX-11/CA-14: arquivo idêntico reusa a storageKey em dois registros")
    void identicalContentShouldShareStorageKey() {
        UUID ticketId = newTicket();

        AttachmentResponse first = uploadPng(ticketId, "primeiro.png");
        AttachmentResponse second = uploadPng(ticketId, "segundo.png");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(storageKeyOf(second.id())).isEqualTo(storageKeyOf(first.id()));
        assertThat(second.scanStatus())
                .as("OB-03: o registro deduplicado nasce em PENDING de todo modo")
                .isEqualTo(ScanStatus.PENDING);
    }

    @Test
    @DisplayName("CA-15/CP-06/SG-07: o mesmo arquivo em dois tenants gera dois binários")
    void deduplicationMustNotCrossTenants() {
        UUID ticketOfA = newTicket();
        AttachmentResponse fromA = uploadPng(ticketOfA, "compartilhado.png");

        UUID ticketOfB = newTicketInTenantB();
        AttachmentResponse fromB =
                asOwnerOfB(
                        () ->
                                attachmentService.upload(
                                        ticketOfB, null, png("compartilhado.png"), null));

        assertThat(storageKeyOf(fromB.id()))
                .as("§6.4: compartilhar binário entre tenants criaria canal de inferência")
                .isNotEqualTo(storageKeyOf(fromA.id()));
    }

    @Test
    @DisplayName("CX-12/CA-16: excluir um de dois referenciadores preserva o binário")
    void deletingOneOfTwoReferencesShouldKeepBinary() {
        UUID ticketId = newTicket();
        AttachmentResponse first = uploadPng(ticketId, "primeiro.png");
        AttachmentResponse second = uploadPng(ticketId, "segundo.png");
        String sharedKey = storageKeyOf(first.id());

        asOwnerOfA(
                () -> {
                    attachmentService.delete(first.id());
                    return null;
                });

        assertThat(storage.exists(sharedKey))
                .as("DoD-06: comprovado por acesso direto ao storage")
                .isTrue();
        assertThat(storageKeyOf(second.id())).isEqualTo(sharedKey);
    }

    @Test
    @DisplayName("CX-13/CA-17: excluir o último referenciador remove o binário do storage")
    void deletingLastReferenceShouldRemoveBinary() {
        UUID ticketId = newTicket();
        AttachmentResponse only = uploadPng(ticketId, "unico.png");
        String storageKey = storageKeyOf(only.id());
        assertThat(storage.exists(storageKey)).isTrue();

        asOwnerOfA(
                () -> {
                    attachmentService.delete(only.id());
                    return null;
                });

        assertThat(storage.exists(storageKey))
                .as(
                        "§19.1: manter o binário acessível por storageKey após a exclusão lógica"
                                + " manteria o dado disponível")
                .isFalse();
    }

    // ── RN-801, quota ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-801/CX-18: a quota soma os registros não excluídos com binário presente")
    void quotaShouldReflectLiveAttachments() {
        UUID ticketId = newTicket();
        long before = asOwnerOfA(() -> quotaService.current()).usedBytes();
        AttachmentResponse created = uploadPng(ticketId, "consome.png");

        assertThat(asOwnerOfA(() -> quotaService.current()).usedBytes())
                .isEqualTo(before + created.sizeBytes());

        asOwnerOfA(
                () -> {
                    attachmentService.delete(created.id());
                    return null;
                });

        assertThat(asOwnerOfA(() -> quotaService.current()).usedBytes()).isEqualTo(before);
    }

    @Test
    @DisplayName("RN-801/FA-09: quota excedida rejeita com DEVTIME-2701 informando o consumo")
    void quotaExceededShouldReportCurrentUsage() {
        UUID ticketId = newTicket();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            // Um pedido de 2 GB não cabe em 1 GB, mesmo com a
                                            // quota vazia — a verificação é sobre o resultado, não
                                            // sobre o consumo atual.
                                            quotaService.assertFits(2L * 1024 * 1024 * 1024);
                                            return null;
                                        }))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure -> {
                            var business = (BusinessRuleException) failure;
                            assertThat(business.getErrorCode().getCode()).isEqualTo("DEVTIME-2701");
                            assertThat(business.getDetails())
                                    .containsKeys("usedBytes", "limitBytes", "attemptedBytes");
                        });
        assertThat(ticketId).isNotNull();
    }

    // ── Isolamento e permissões ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CA-26/SG-06/SG-15: anexo de outro tenant responde 404, nunca 403")
    void attachmentFromAnotherTenantMustBeNotFound() {
        UUID ticketId = newTicket();
        AttachmentResponse fromA = uploadPng(ticketId, "do-tenant-a.png");

        assertThatThrownBy(
                        () ->
                                asOwnerOfB(
                                        () -> {
                                            attachmentService.delete(fromA.id());
                                            return null;
                                        }))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> asOwnerOfB(() -> downloadService.download(fromA.id(), null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("SG-06: ticket de outro tenant como alvo responde 404")
    void uploadToForeignTicketMustBeNotFound() {
        UUID ticketOfA = newTicket();

        assertThatThrownBy(
                        () ->
                                asOwnerOfB(
                                        () ->
                                                attachmentService.upload(
                                                        ticketOfA, null, png("intruso.png"), null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private UUID newComment(UUID ticketId) {
        return asOwnerOfA(
                        () ->
                                commentService.create(
                                        ticketId,
                                        new CommentCreateRequest("Comentário com anexos", null)))
                .id();
    }

    private AttachmentResponse uploadToComment(UUID commentId, String fileName) {
        return asOwnerOfA(
                () ->
                        attachmentService.upload(
                                null,
                                commentId,
                                AttachmentFixtures.upload(
                                        fileName, "image/png", AttachmentFixtures.png()),
                                null));
    }

    private UUID newTicketInTenantB() {
        var contract = asOwnerOfB(() -> scenario.activeContract(scenario.activeClient()));
        return asOwnerOfB(
                        () ->
                                ticketService.create(
                                        new com.devtime.ticket.dto.TicketRequests
                                                .TicketCreateRequest(
                                                contract.id(),
                                                "Ticket do tenant B",
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

    /** Conta quantas vezes o conteúdo foi aberto, para provar CA-03. */
    private static final class CountingContent
            implements com.devtime.attachment.domain.UploadContent {

        private final com.devtime.attachment.domain.UploadContent delegate;
        private int streamsOpened;

        private CountingContent(com.devtime.attachment.domain.UploadContent delegate) {
            this.delegate = delegate;
        }

        int streamsOpened() {
            return streamsOpened;
        }

        @Override
        public String originalFileName() {
            return delegate.originalFileName();
        }

        @Override
        public String declaredContentType() {
            return delegate.declaredContentType();
        }

        @Override
        public long sizeBytes() {
            return delegate.sizeBytes();
        }

        @Override
        public java.io.InputStream openStream() throws java.io.IOException {
            streamsOpened++;
            return delegate.openStream();
        }
    }
}
