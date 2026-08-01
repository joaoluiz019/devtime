package com.devtime.timer;

import com.devtime.timer.dto.TimerRequests.TimerStopRequest;
import com.devtime.timer.dto.TimerResponses.ActiveTimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerStopResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visão de equipe e encerramento forçado (worklogs.md §12).
 *
 * <p>OWN-05 é a regra de propriedade mais restritiva do sistema: o cronômetro pertence
 * <b>exclusivamente</b> ao seu usuário. Nem {@code MANAGER} o opera — apenas {@code OWNER} e {@code
 * ADMIN}, por {@code TIMER_STOP_ANY}, e ainda assim com notificação ao dono. Interferir sem que a
 * pessoa saiba produziria um registro de horas que ela não reconhece como seu.
 */
@RestController
@RequestMapping("/api/v1/timers")
@RequiredArgsConstructor
@Tag(name = "Timers — equipe", description = "Cronômetros ativos e encerramento forçado")
public class TimerAdminController {

    private final TimerService timerService;
    private final TimerQueryService queryService;

    @GetMapping("/active")
    @Operation(
            summary = "Cronômetros ativos da organização",
            description =
                    "Exige `TIMER_VIEW_ANY`. §19.1: mostra **apenas** que existe trabalho em"
                            + " andamento e em qual ticket — nunca a descrição nem o histórico de"
                            + " pausas, que revelariam o ritmo de trabalho de cada pessoa.")
    @ApiResponse(responseCode = "200", description = "Cronômetros ativos do tenant")
    public List<ActiveTimerResponse> active() {
        return queryService.activeInTenant();
    }

    @PostMapping("/{id}/force-stop")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Encerra o cronômetro de outra pessoa",
            description =
                    "Exige `TIMER_STOP_ANY` (OWNER e ADMIN). O **dono é notificado**. As mesmas"
                            + " validações de 008 se aplicam, e a mesma preservação: sem descrição"
                            + " válida, o cronômetro do outro permanece ativo (CX-20).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registro gerado; dono notificado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem TIMER_STOP_ANY"),
        @ApiResponse(responseCode = "422", description = "Códigos de validação de 008")
    })
    public TimerStopResponse forceStop(
            @PathVariable UUID id, @Valid @RequestBody TimerStopRequest request) {
        return timerService.forceStop(id, request);
    }
}
