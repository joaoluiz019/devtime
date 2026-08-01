package com.devtime.timer;

import com.devtime.timer.dto.TimerRequests.TimerStartRequest;
import com.devtime.timer.dto.TimerRequests.TimerStopRequest;
import com.devtime.timer.dto.TimerRequests.TimerUpdateRequest;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerStopResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints do cronômetro do usuário (worklogs.md §9 a §11).
 *
 * <p>Todas as rotas de operação usam {@code /current} e resolvem o cronômetro pelo <b>token</b>,
 * nunca por identificador de caminho (OWN-05, SG-01): não existe rota capaz de operar o cronômetro
 * de outra pessoa, exceto o encerramento forçado de {@link TimerAdminController}.
 *
 * <p>BR-089: pausar, retomar e encerrar são {@code POST} de ação, nunca {@code PATCH} no campo
 * {@code status}.
 */
@RestController
@RequestMapping("/api/v1/timers")
@RequiredArgsConstructor
@Tag(name = "Timers", description = "Cronômetro com estado persistido no servidor")
public class TimerController {

    private final TimerService timerService;
    private final TimerQueryService queryService;

    @GetMapping("/current")
    @Operation(
            summary = "Cronômetro ativo do usuário",
            description =
                    "Chamado ao carregar toda tela — o cronômetro é um componente global. O cliente"
                            + " calcula o tempo decorrido localmente a partir de `startedAt`,"
                            + " `lastResumedAt` e `accumulatedActiveSeconds`; **não** consulte este"
                            + " endpoint a cada segundo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cronômetro ativo"),
        @ApiResponse(responseCode = "204", description = "Nenhum cronômetro ativo")
    })
    public ResponseEntity<TimerResponse> current() {
        return queryService
                .current()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @Operation(
            summary = "Inicia um cronômetro",
            description =
                    "RN-150: no máximo um ativo por **usuário**, entre todos os tenants — participar"
                            + " de duas organizações não torna a pessoa duas. `?stopCurrent=true`"
                            + " encerra o atual e inicia o novo em uma operação atômica (RN-166): se"
                            + " o encerramento falhar, nada acontece.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cronômetro iniciado"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2150 — já existe cronômetro ativo"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2306 ou DEVTIME-2104")
    })
    public ResponseEntity<TimerResponse> start(
            @Valid @RequestBody TimerStartRequest request,
            @RequestParam(defaultValue = "false") boolean stopCurrent) {
        TimerResponse started = timerService.start(request, stopCurrent);
        return ResponseEntity.created(URI.create("/api/v1/timers/current")).body(started);
    }

    @PatchMapping("/current")
    @Operation(
            summary = "Edita o cronômetro em execução",
            description =
                    "RN-161: ticket, categoria, descrição e faturável. `startedAt` está ausente do"
                            + " payload — alterá-lo seria reescrever quando o trabalho começou.")
    @ApiResponse(responseCode = "200", description = "Cronômetro atualizado")
    public TimerResponse update(@Valid @RequestBody TimerUpdateRequest request) {
        return timerService.update(request);
    }

    @PostMapping("/current/pause")
    @Operation(
            summary = "Pausa o cronômetro",
            description = "RN-153: exige `RUNNING`. Pausar um já pausado é erro, não idempotência.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cronômetro pausado"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2153")
    })
    public TimerResponse pause() {
        return timerService.pause();
    }

    @PostMapping("/current/resume")
    @Operation(summary = "Retoma o cronômetro", description = "RN-155: exige `PAUSED`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cronômetro retomado"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2155")
    })
    public TimerResponse resume() {
        return timerService.resume();
    }

    @PostMapping("/current/stop")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Encerra o cronômetro e gera o registro de horas",
            description =
                    "RN-159: aplica **todas** as validações de 008 — o cronômetro não tem caminho"
                            + " próprio de regra. RN-160: em qualquer falha de validação o **cronômetro"
                            + " permanece ativo**, e a resposta de erro traz o código da regra violada"
                            + " para que a interface oriente a correção.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registro criado, com o saldo atualizado"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2010 — cronômetro já encerrado"),
        @ApiResponse(
                responseCode = "422",
                description =
                        "DEVTIME-2105 (sem descrição), 2102 (sobreposição), 2103, 2116, 2220 (saldo)"
                                + " ou 2306 — em todos, o cronômetro permanece ativo")
    })
    public TimerStopResponse stop(@Valid @RequestBody TimerStopRequest request) {
        return timerService.stop(request);
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Descarta o cronômetro",
            description =
                    "RN-162: irreversível e sem gerar registro de horas. Exige `?confirm=true` — é a"
                            + " única operação do sistema que destrói trabalho registrado sem"
                            + " contrapartida, e o tempo descartado fica na auditoria.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cronômetro descartado"),
        @ApiResponse(responseCode = "422", description = "Confirmação ausente — nada acontece")
    })
    public void discard(@RequestParam(defaultValue = "false") boolean confirm) {
        timerService.discard(confirm);
    }
}
