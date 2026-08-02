package com.devtime.attachment;

import com.devtime.attachment.dto.AttachmentResponses.AttachmentListResponse;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Anexos do ticket (tickets.md §11).
 *
 * <p>BR-080: nenhuma regra de negócio aqui. A permissão é verificada na camada de serviço (BR-161),
 * e o mesmo vale para a ordem de §6.1 — o controller apenas adapta o {@code MultipartFile} e
 * repassa.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Anexos", description = "Arquivos anexados a tickets e comentários")
public class TicketAttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    @Operation(
            summary = "Lista os anexos do ticket",
            description =
                    "`canDownload` e `canDelete` são calculados no servidor: RN-803 e OWN-07 não"
                            + " são reimplementadas pelo cliente. `storageKey` e `checksum` nunca"
                            + " aparecem na resposta.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Anexos do ticket"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — ticket de outro tenant")
    })
    public AttachmentListResponse list(@PathVariable UUID ticketId) {
        return attachmentService.listByTicket(ticketId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Envia um anexo ao ticket",
            description =
                    "A validação segue a ordem normativa de §6.1: alvo, limite de RN-806, tamanho,"
                            + " quota, allowlist e **assinatura binária**. O arquivo é criado em"
                            + " `PENDING` e o **download permanece bloqueado** até a verificação"
                            + " antivírus concluir (RN-803).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Anexo criado em PENDING — download ainda bloqueado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem ATTACHMENT_UPLOAD"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — ticket de outro tenant"),
        @ApiResponse(
                responseCode = "413",
                description = "DEVTIME-2701 — acima de 10 MB ou quota do tenant excedida"),
        @ApiResponse(
                responseCode = "415",
                description =
                        "DEVTIME-2702 — tipo fora da allowlist ou assinatura binária divergente do"
                                + " tipo declarado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2704 — 20 anexos por ticket")
    })
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable UUID ticketId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        AttachmentResponse created =
                attachmentService.upload(
                        ticketId,
                        null,
                        new MultipartUploadContent(file),
                        RequestIpResolver.resolve(request));
        // BR-088: 201 sempre acompanha Location. A rota de manutenção é /attachments/{id}.
        return ResponseEntity.created(URI.create("/api/v1/attachments/" + created.id()))
                .body(created);
    }
}
