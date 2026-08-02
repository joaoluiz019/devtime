package com.devtime.attachment;

import com.devtime.attachment.dto.AttachmentResponses.DownloadResponse;
import com.devtime.attachment.dto.AttachmentResponses.QuotaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Download e exclusão de um anexo (tickets.md §11).
 *
 * <p><b>Não existe rota de atualização</b> (CP-13, RN-011, CA-11). A ausência não é esquecimento: é
 * a implementação da regra. Alterar o {@code contentType} depois da verificação permitiria burlar a
 * validação de assinatura, e qualquer rota que aceitasse alterar {@code scanStatus} seria o caminho
 * de liberação manual que CP-02 proíbe.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Anexos", description = "Arquivos anexados a tickets e comentários")
public class AttachmentController {

    private final AttachmentDownloadService downloadService;
    private final AttachmentService attachmentService;
    private final QuotaService quotaService;

    /**
     * Redireciona para a URL assinada (CP-15, OB-06).
     *
     * <p>{@code 302} e não {@code 200} com o conteúdo: o binário não passa pela aplicação. Servi-lo
     * aqui consumiria banda e memória proporcionais ao tráfego de download, e faria a aplicação
     * manipular conteúdo externo depois do upload — exatamente o que OB-06 evita.
     */
    @GetMapping("/attachments/{id}/download")
    @Operation(
            summary = "Baixa um anexo",
            description =
                    "RN-803: liberado **apenas** com `scanStatus = CLEAN`. `PENDING` e `FAILED`"
                            + " respondem 409; `INFECTED` responde 403 e o binário já foi removido"
                            + " do storage. Não existe liberação manual (§6.3). Responde 302 para"
                            + " uma URL assinada de validade curta — o binário nunca passa pela"
                            + " aplicação. Todo download é auditado (§18).")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redireciona para a URL assinada"),
        @ApiResponse(
                responseCode = "403",
                description = "DEVTIME-2703 — arquivo INFECTED, bloqueado por segurança"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — anexo de outro tenant"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2703 — em verificação (PENDING) ou verificação falhada")
    })
    public ResponseEntity<Void> download(@PathVariable UUID id, HttpServletRequest request) {
        DownloadResponse download =
                downloadService.download(id, RequestIpResolver.resolve(request));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(download.url())).build();
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Exclui um anexo",
            description =
                    "Exclusão lógica (RN-003). O binário é removido do storage **apenas** se este"
                            + " for o último registro que o referencia (RN-805). OWN-07: o autor"
                            + " exclui a qualquer momento, sem janela temporal; quem possui"
                            + " `ATTACHMENT_DELETE_ANY` modera.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Excluído logicamente"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1103 — não é quem enviou"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — anexo de outro tenant")
    })
    public void delete(@PathVariable UUID id) {
        attachmentService.delete(id);
    }

    @GetMapping("/attachments/quota")
    @Operation(
            summary = "Consumo de armazenamento da organização",
            description =
                    "RN-801: 1 GB no plano gratuito. Conta apenas registros não excluídos com"
                            + " binário presente (CX-18).")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Consumo atual"))
    public QuotaResponse quota() {
        return quotaService.current();
    }
}
