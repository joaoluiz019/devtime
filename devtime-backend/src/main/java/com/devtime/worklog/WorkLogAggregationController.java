package com.devtime.worklog;

import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogTotalsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agregações de registro de horas (worklogs.md §7 e §8).
 *
 * <p>Separado de {@link WorkLogController} porque são consultas de natureza distinta — leitura
 * agregada, sem recurso individual — e porque mantém aquele controller dentro do limite de BR-010
 * sem que a divisão seja arbitrária.
 */
@RestController
@RequestMapping("/api/v1/work-logs")
@RequiredArgsConstructor
@Tag(name = "Work Logs — agregações", description = "Calendário e totais por filtro")
public class WorkLogAggregationController {

    private final WorkLogAggregationService aggregationService;

    @GetMapping("/calendar")
    @Operation(
            summary = "Totais por dia no intervalo",
            description =
                    "P22. O agrupamento é por `workDate`, que já é a data local do tenant (RN-108):"
                            + " agrupar em UTC colocaria uma sessão das 22h no dia seguinte.")
    @ApiResponse(responseCode = "200", description = "Dias com total, faturável e contagem")
    public WorkLogCalendarResponse calendar(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID userId) {
        return aggregationService.calendar(from, to, userId);
    }

    @GetMapping("/totals")
    @Operation(
            summary = "Totais dos filtros aplicados",
            description =
                    "P21. Recebe os **mesmos** filtros da listagem: o total exibido no topo precisa"
                            + " somar exatamente as linhas mostradas abaixo, inclusive sob o escopo de"
                            + " dados de `MEMBER` (SG-03).")
    @ApiResponse(responseCode = "200", description = "Totais agregados e quebra por categoria")
    public WorkLogTotalsResponse totals(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Boolean billable,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String search) {
        return aggregationService.totals(
                new WorkLogFilter(
                        userId,
                        ticketId,
                        contractId,
                        clientId,
                        categoryId,
                        tagIds,
                        dateFrom,
                        dateTo,
                        billable,
                        source,
                        search));
    }
}
