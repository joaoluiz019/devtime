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
 * Anexos do comentário (tickets.md §11, FA-01).
 *
 * <p>Rota separada porque o alvo é outro, e INV-ATT-01 exige que seja exatamente um: com uma rota
 * única recebendo os dois identificadores, o estado proibido — dois alvos, ou nenhum — seria
 * expressável em toda requisição.
 *
 * <p>CX-19: o limite aqui é 5 (RN-806), independente dos 20 do ticket.
 */
@RestController
@RequestMapping("/api/v1/comments/{commentId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Anexos", description = "Arquivos anexados a tickets e comentários")
public class CommentAttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    @Operation(summary = "Lista os anexos do comentário")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Anexos do comentário"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — comentário de outro tenant")
    })
    public AttachmentListResponse list(@PathVariable UUID commentId) {
        return attachmentService.listByComment(commentId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Envia um anexo ao comentário",
            description =
                    "Mesma ordem normativa de §6.1 do envio em ticket. RN-806: máximo de 5 anexos"
                            + " por comentário — limite independente do limite do ticket (CX-19).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Anexo criado em PENDING — download ainda bloqueado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem ATTACHMENT_UPLOAD"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — comentário de outro tenant"),
        @ApiResponse(responseCode = "413", description = "DEVTIME-2701"),
        @ApiResponse(responseCode = "415", description = "DEVTIME-2702"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2704 — 5 anexos por comentário")
    })
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable UUID commentId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        AttachmentResponse created =
                attachmentService.upload(
                        null,
                        commentId,
                        new MultipartUploadContent(file),
                        RequestIpResolver.resolve(request));
        return ResponseEntity.created(URI.create("/api/v1/attachments/" + created.id()))
                .body(created);
    }
}
