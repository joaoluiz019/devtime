package com.devtime.contract;

import com.devtime.contract.dto.BalanceRequests.AdjustmentRequest;
import com.devtime.contract.dto.BalanceRequests.ClosePeriodRequest;
import com.devtime.contract.dto.BalanceRequests.ReopenPeriodRequest;
import com.devtime.contract.dto.BalanceResponses.AdjustmentResponse;
import com.devtime.contract.dto.BalanceResponses.ClosePeriodResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodSnapshotResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodStatementResponse;
import com.devtime.contract.dto.BalanceResponses.ReopenPeriodResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Banco de horas do período (contracts.md §10 a §13).
 *
 * <p><b>Não existe rota de edição nem de exclusão de ajuste</b> (RN-236) <b>nem de alteração de
 * snapshot</b> (INV-SNP-01). A ausência é deliberada e faz parte da garantia: o que não tem rota
 * não é feito por engano.
 *
 * <p>BR-089: fechar e reabrir são {@code POST} de ação, nunca {@code PATCH} no campo {@code
 * status}.
 */
@RestController
@RequestMapping("/api/v1/contract-periods")
@RequiredArgsConstructor
@Tag(name = "Banco de horas", description = "Saldo, extrato, ajustes, fechamento e reabertura")
public class ContractPeriodBalanceController {

    private final BalanceService balanceService;
    private final PeriodStatementService statementService;
    private final AdjustmentService adjustmentService;
    private final PeriodClosingService closingService;
    private final PeriodReopeningService reopeningService;
    private final SnapshotService snapshotService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Saldo do período",
            description =
                    "RN-218 a RN-223. `isPartial` é verdadeiro em `OPEN` e `REOPENED` (RN-702) e"
                            + " deve ser exibido: um número em evolução apresentado sem essa marcação"
                            + " será lido como final.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Saldo calculado"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002")
    })
    public PeriodBalanceResponse balance(@PathVariable UUID id) {
        return balanceService.getBalance(id);
    }

    @GetMapping("/{id}/statement")
    @Operation(
            summary = "Extrato explicativo do período",
            description =
                    "Contratado, transportado, ajustes e registros de horas em ordem cronológica,"
                            + " com o saldo acumulado após cada movimento. Um saldo que o cliente não"
                            + " consegue conferir é tão ruim quanto um saldo errado.")
    @ApiResponse(responseCode = "200", description = "Extrato do período")
    public PeriodStatementResponse statement(@PathVariable UUID id) {
        return statementService.statement(id);
    }

    @GetMapping("/{id}/adjustments")
    @Operation(summary = "Ajustes aplicados ao período")
    @ApiResponse(responseCode = "200", description = "Ajustes em ordem cronológica")
    public List<AdjustmentResponse> adjustments(@PathVariable UUID id) {
        return adjustmentService.listByPeriod(id);
    }

    @PostMapping("/{id}/adjustments")
    @Operation(
            summary = "Aplica um ajuste de saldo",
            description =
                    "RN-238: apenas OWNER e ADMIN — conceder ou retirar horas contratadas é decisão"
                            + " de quem responde comercialmente. RN-236: o ajuste é **imutável**; não"
                            + " existe rota de edição, e a correção se faz por um estorno de sinal"
                            + " contrário.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Ajuste aplicado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem PERIOD_ADJUST"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2235 — período não aberto"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2215 (justificativa) ou DEVTIME-2237 (saldo negativo)")
    })
    public ResponseEntity<AdjustmentResponse> applyAdjustment(
            @PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request) {
        AdjustmentResponse created = adjustmentService.apply(id, request);
        return ResponseEntity.created(URI.create("/api/v1/contract-periods/" + id + "/adjustments"))
                .body(created);
    }

    @PostMapping("/{id}/close")
    @Operation(
            summary = "Fecha o período",
            description =
                    "RN-241: sequência atômica de sete passos — reconciliar, calcular carry-over,"
                            + " travar registros, gerar snapshot com checksum, marcar como fechado,"
                            + " propagar `carriedIn` e notificar. Falha em qualquer passo reverte"
                            + " todos. `confirmed` é obrigatório antes do `endDate` (RN-239).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumo do fechamento"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem PERIOD_CLOSE"),
        @ApiResponse(
                responseCode = "409",
                description =
                        "DEVTIME-2239 (antes do fim sem confirmação), DEVTIME-2240 (cronômetro"
                                + " ativo, inclusive pausado) ou DEVTIME-2010")
    })
    public ClosePeriodResponse close(
            @PathVariable UUID id, @Valid @RequestBody ClosePeriodRequest request) {
        return closingService.close(id, request);
    }

    @PostMapping("/{id}/reopen")
    @Operation(
            summary = "Reabre um período fechado",
            description =
                    "RN-242: exige OWNER/ADMIN e justificativa — é a operação que altera um relatório"
                            + " **já entregue**. RN-244: reabre-se do mais recente para o mais antigo."
                            + " RN-243/INV-SNP-01: o snapshot anterior é **preservado**; o"
                            + " refechamento gera um novo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Período reaberto"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem PERIOD_REOPEN"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2244 — existe período posterior fechado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2215 — justificativa obrigatória")
    })
    public ReopenPeriodResponse reopen(
            @PathVariable UUID id, @Valid @RequestBody ReopenPeriodRequest request) {
        return reopeningService.reopen(id, request);
    }

    @GetMapping("/{id}/snapshot")
    @Operation(
            summary = "Snapshot mais recente do período",
            description =
                    "Cópia congelada do relatório (RN-701). `checksumValid = false` indica"
                            + " adulteração e gera alerta operacional — o snapshot **não** é corrigido"
                            + " automaticamente (CX-21).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot encontrado"),
        @ApiResponse(responseCode = "404", description = "Período ainda não fechado")
    })
    public ResponseEntity<PeriodSnapshotResponse> snapshot(@PathVariable UUID id) {
        return snapshotService
                .latest(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
