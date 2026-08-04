package com.devtime.report;

import com.devtime.report.domain.ExportStatus;
import com.devtime.report.dto.ExportRequests.ExportRequest;
import com.devtime.report.dto.ExportResponses.ExportExecutionResponse;
import com.devtime.report.dto.ExportResponses.ExportResponse;
import com.devtime.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação de relatórios (§8 de reports.md).
 *
 * <p>BR-087 não se aplica a {@link #request}: o status é {@code 200} ou {@code 202} conforme o modo
 * decidido por RN-706, e um {@code @ResponseStatus} fixo mentiria em um dos dois casos. Por isso e
 * só por isso o método devolve {@code ResponseEntity}.
 *
 * <p>O download responde {@code 302} para uma URL assinada (§8.3): o binário <b>nunca</b> passa
 * pela aplicação. Servir o arquivo aqui dobraria a banda, prenderia um thread pelo tempo do
 * download e tornaria a expiração de 15 minutos irrelevante.
 */
@RestController
@RequestMapping("/api/v1/reports/exports")
@RequiredArgsConstructor
@Tag(name = "Exportações", description = "Materialização dos relatórios em PDF, XLSX e CSV")
public class ReportExportController {

    private final ExportService exportService;

    @PostMapping
    @Operation(
            summary = "Solicita uma exportação",
            description =
                    "Até 5.000 linhas o arquivo é gerado na própria requisição e a resposta é"
                            + " `200` com URL assinada de 15 minutos. **Acima** de 5.000 a resposta"
                            + " é `202` com `pollUrl`, e o solicitante é notificado ao concluir"
                            + " (RN-706). O header `Idempotency-Key` faz duas requisições idênticas"
                            + " devolverem a **mesma** exportação (CE-R-12).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Arquivo gerado; URL assinada na resposta"),
        @ApiResponse(responseCode = "202", description = "Enfileirada; acompanhe por `pollUrl`"),
        @ApiResponse(
                responseCode = "400",
                description = "`DEVTIME-3001` intervalo acima de 366 dias"),
        @ApiResponse(
                responseCode = "403",
                description = "`DEVTIME-1101` `MEMBER` fora do escopo `myWorkLogs`"),
        @ApiResponse(
                responseCode = "422",
                description =
                        "`DEVTIME-3003` parâmetros incompatíveis com o tipo; `DEVTIME-3007`"
                                + " agrupamento não suportado"),
        @ApiResponse(responseCode = "429", description = "Limite de 20 exportações por hora")
    })
    public ResponseEntity<ExportResponse> request(
            @Valid @RequestBody ExportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ExportResponse response = exportService.request(request, idempotencyKey);

        if (response.status() == ExportStatus.QUEUED) {
            return ResponseEntity.accepted().body(response);
        }
        // BR-088 não se aplica: não há recurso novo a apontar por `Location` além do próprio corpo,
        // e a URL de download é assinada e expira — colocá-la em `Location` a tornaria um endereço
        // permanente aos olhos de qualquer intermediário que registre cabeçalhos.
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Lista as próprias exportações",
            description =
                    "SG-04: restrita ao solicitante. A exportação de um colega é indistinguível de"
                            + " inexistente, pelo mesmo motivo que um recurso de outro tenant é.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de exportações"),
        @ApiResponse(responseCode = "400", description = "`DEVTIME-2006` size acima de 100")
    })
    public PageResponse<ExportExecutionResponse> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return exportService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Acompanha uma exportação",
            description = "Traz `progress` durante `PROCESSING` e os filtros aplicados (RN-707).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado da exportação"),
        @ApiResponse(responseCode = "404", description = "`DEVTIME-2002` exportação de terceiro")
    })
    public ExportExecutionResponse get(@PathVariable UUID id) {
        return exportService.get(id);
    }

    @GetMapping("/{id}/download")
    @Operation(
            summary = "Baixa o arquivo",
            description =
                    "`302` para uma URL assinada válida por 15 minutos (RN-712). Expirada a URL,"
                            + " uma nova solicitação gera outra assinatura **sem regerar o"
                            + " arquivo** (FA-13). Todo download é auditado (§18).")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redireciona para a URL assinada"),
        @ApiResponse(
                responseCode = "409",
                description = "`DEVTIME-3004` ainda não concluída; `DEVTIME-3006` geração falhou"),
        @ApiResponse(responseCode = "410", description = "`DEVTIME-3005` arquivo expirado"),
        @ApiResponse(responseCode = "404", description = "`DEVTIME-2002` exportação de terceiro")
    })
    public ResponseEntity<Void> download(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(exportService.downloadUrl(id)))
                .build();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancela uma exportação enfileirada",
            description =
                    "Permitido apenas em `QUEUED` (FA-15). Em `PROCESSING` responde"
                            + " `DEVTIME-3004`: o worker já está gerando, e cancelar exigiria"
                            + " interrompê-lo no meio.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cancelada"),
        @ApiResponse(
                responseCode = "409",
                description = "`DEVTIME-3004` exportação já em processamento"),
        @ApiResponse(responseCode = "404", description = "`DEVTIME-2002` exportação de terceiro")
    })
    public void cancel(@PathVariable UUID id) {
        exportService.cancel(id);
    }
}
