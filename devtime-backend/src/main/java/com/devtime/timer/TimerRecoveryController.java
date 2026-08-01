package com.devtime.timer;

import com.devtime.timer.dto.TimerRequests.TimerRecoverRequest;
import com.devtime.timer.dto.TimerResponses.AbandonedTimerResponse;
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
 * Recuperação de cronômetros abandonados (worklogs.md §12).
 *
 * <p>RN-164 marca como abandonado em vez de encerrar automaticamente porque encerrar exigiria
 * <b>inventar</b> um horário de término, e o sistema não decide quanto tempo alguém trabalhou
 * (PR-03). A recuperação existe para que a pessoa informe o horário real — o único que é verdade.
 */
@RestController
@RequestMapping("/api/v1/timers")
@RequiredArgsConstructor
@Tag(name = "Timers — recuperação", description = "Cronômetros abandonados e sua recuperação")
public class TimerRecoveryController {

    private final TimerService timerService;

    @GetMapping("/abandoned")
    @Operation(
            summary = "Cronômetros abandonados do usuário",
            description =
                    "RN-164/RN-165: marcados pelo job após o limiar de abandono e recuperáveis por 7"
                            + " dias. `recoverableUntil` traz o prazo no fuso do tenant.")
    @ApiResponse(responseCode = "200", description = "Abandonados recuperáveis")
    public List<AbandonedTimerResponse> abandoned() {
        return timerService.abandoned();
    }

    @PostMapping("/{id}/recover")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Recupera um cronômetro abandonado",
            description =
                    "RN-165: exige o horário real de término. O registro gerado passa por **todas**"
                            + " as validações de 008 — um `endedAt` que produza 25 horas é rejeitado"
                            + " por RN-103 e o cronômetro permanece abandonado (CX-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registro gerado a partir do cronômetro"),
        @ApiResponse(
                responseCode = "409",
                description =
                        "DEVTIME-2165 — fora da janela de 7 dias; DEVTIME-2121 — período fechado"),
        @ApiResponse(responseCode = "422", description = "Códigos de validação de 008")
    })
    public TimerStopResponse recover(
            @PathVariable UUID id, @Valid @RequestBody TimerRecoverRequest request) {
        return timerService.recover(id, request);
    }
}
