package com.devtime.worklog;

import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.BalanceResponses.OverageCheckResponse;
import com.devtime.contract.dto.ContractResponses.ContractPeriodRefResponse;
import com.devtime.contract.dto.ContractResponses.ContractWorkLogRefResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.tenant.TenantSettingsService;
import com.devtime.tenant.dto.TenantSettings;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketResponses.TicketWorkLogRefResponse;
import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.domain.WorkLogInterval;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogValidateRequest;
import com.devtime.worklog.dto.WorkLogResponses.BalancePreviewResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalculationResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogConflictResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogValidateResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogWarning;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validação prévia do registro de horas (ver {@link WorkLogValidationService}).
 *
 * <p>{@code @Transactional(readOnly = true)} em toda a classe e <b>nenhum</b> método de escrita:
 * CP-19 exige que nada seja persistido, e uma transação somente leitura torna isso verificável em
 * inspeção, não apenas em revisão (CA-19).
 *
 * <p>Cada verificação é executada isoladamente, capturando a exceção e acumulando o código do erro.
 * É o oposto da criação, que interrompe na primeira falha — aqui o objetivo é permitir uma única
 * rodada de correção.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkLogValidationServiceImpl implements WorkLogValidationService {

    private final WorkLogCalculator calculator;
    private final RoundingPolicy roundingPolicy;
    private final WorkDateResolver workDateResolver;
    private final OverlapDetector overlapDetector;
    private final WorkLogValidator validator;
    private final ContractValidityValidator contractValidityValidator;
    private final RetroactiveWindowPolicy retroactiveWindowPolicy;
    private final WorkLogOwnershipPolicy ownershipPolicy;
    private final WorkLogMapper mapper;
    private final TicketService ticketService;
    private final ContractService contractService;
    private final ContractPeriodService contractPeriodService;
    private final BalanceService balanceService;
    private final TenantSettingsService tenantSettingsService;

    @Override
    @PreAuthorize("hasPermission(null, 'WORKLOG_CREATE')")
    public WorkLogValidateResponse validate(WorkLogValidateRequest request) {
        List<WorkLogWarning> errors = new ArrayList<>();
        List<WorkLogWarning> warnings = new ArrayList<>();
        List<WorkLogConflictResponse> conflicts = new ArrayList<>();

        TenantSettings settings = tenantSettingsService.current();
        UUID ownerId = ownershipPolicy.resolveOwner(request.userId());
        WorkLogInterval interval = new WorkLogInterval(request.startedAt(), request.endedAt());

        collect(errors, () -> validator.assertChronological(interval)); // RN-114

        int grossMinutes = calculator.grossMinutes(interval.startedAt(), interval.endedAt());
        int pausedMinutes = request.pausedMinutes() == null ? 0 : request.pausedMinutes();
        int netBeforeRounding = calculator.netMinutes(grossMinutes, pausedMinutes);
        int netMinutes = roundingPolicy.roundDown(netBeforeRounding, settings.roundingMinutes());
        LocalDate workDate = workDateResolver.resolve(interval.startedAt());

        collect(errors, () -> validator.assertWithinMaxDuration(grossMinutes)); // RN-103
        collect(errors, () -> validator.assertNotInFuture(interval)); // RN-118
        collect(
                errors,
                () -> validator.assertFutureDateAllowed(workDate, settings.allowFutureWorkLogs()));
        collect(
                errors,
                () ->
                        retroactiveWindowPolicy.assertWithinWindow(
                                workDate, settings.retroactiveLimitDays())); // RN-120
        collect(
                errors,
                () -> validator.assertPausedMinutesCoherent(pausedMinutes, grossMinutes)); // RN-116
        collect(
                errors,
                () -> validator.assertPositiveNetMinutes(netMinutes, netBeforeRounding)); // RN-115
        if (request.description() != null) {
            collect(errors, () -> validator.requireDescription(request.description())); // RN-105
        }

        // RN-102: o conflito é relatado, não lançado — o usuário precisa ver a sobreposição e o
        // saldo na mesma resposta para decidir o que corrigir.
        overlapDetector
                .findConflict(ownerId, interval, request.excludeWorkLogId())
                .ifPresent(
                        conflict -> {
                            conflicts.add(toConflict(conflict));
                            errors.add(
                                    new WorkLogWarning(
                                            ErrorCode.WORKLOG_OVERLAP.getCode(),
                                            "Já existe um registro de horas neste intervalo",
                                            0));
                        });

        boolean billable = request.billable() == null || request.billable();
        BalancePreviewResponse preview =
                previewBalance(request, workDate, netMinutes, billable, errors, warnings);

        return new WorkLogValidateResponse(
                errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings),
                List.copyOf(conflicts),
                new WorkLogCalculationResponse(
                        grossMinutes,
                        pausedMinutes,
                        netBeforeRounding,
                        netMinutes,
                        calculator.billableMinutes(netMinutes, billable),
                        workDate,
                        mapper.durationLabel(netMinutes)),
                preview);
    }

    /**
     * Prévia do saldo: quanto há, quanto será consumido e o que sobra (§21.2, {@code
     * dt-balance-preview}).
     *
     * <p>Mostrar o "depois" ao lado do "antes" é o que permite ao usuário marcar o registro como
     * não faturável <b>antes</b> de esbarrar em {@code BLOCK}, em vez de descobrir na tentativa de
     * salvar.
     */
    private BalancePreviewResponse previewBalance(
            WorkLogValidateRequest request,
            LocalDate workDate,
            int netMinutes,
            boolean billable,
            List<WorkLogWarning> errors,
            List<WorkLogWarning> warnings) {
        Optional<ContractPeriodRefResponse> period = resolvePeriod(request, workDate, errors);
        if (period.isEmpty()) {
            return null;
        }

        int additional = calculator.billableMinutes(Math.max(netMinutes, 0), billable);
        OverageCheckResponse check = balanceService.checkOverage(period.get().id(), additional);
        if (check.wouldExceed()) {
            // A política decide se isso é erro ou aviso — a mesma decisão de
            // OveragePolicyEvaluator, aqui sem lançar (CE-17).
            WorkLogWarning overage =
                    new WorkLogWarning(
                            "BLOCK".equals(check.overagePolicy())
                                    ? ErrorCode.PERIOD_BALANCE_INSUFFICIENT.getCode()
                                    : ErrorCode.PERIOD_OVERAGE_WARNING.getCode(),
                            "BLOCK".equals(check.overagePolicy())
                                    ? "Saldo insuficiente no contrato"
                                    : "Aviso: saldo do contrato excedido",
                            check.exceedingMinutes());
            if ("BLOCK".equals(check.overagePolicy())) {
                errors.add(overage); // RN-231
            } else if ("WARN".equals(check.overagePolicy())) {
                warnings.add(overage); // RN-232
            }
        }

        return new BalancePreviewResponse(
                period.get().id(),
                check.availableMinutes(),
                check.consumedMinutes(),
                check.consumedMinutes() + additional,
                check.availableMinutes() - (check.consumedMinutes() + additional));
    }

    private Optional<ContractPeriodRefResponse> resolvePeriod(
            WorkLogValidateRequest request, LocalDate workDate, List<WorkLogWarning> errors) {
        try {
            TicketWorkLogRefResponse ticket = ticketService.getRefForWorkLog(request.ticketId());
            ContractWorkLogRefResponse contract =
                    contractService.getWorkLogRef(ticket.contractId());
            contractValidityValidator.assertWithinValidity(
                    request.startedAt(), contract.startDate(), contract.endDate()); // RN-117

            Optional<ContractPeriodRefResponse> period =
                    contractPeriodService.resolvePeriodRef(contract.id(), workDate);
            if (period.isEmpty()) {
                errors.add(
                        new WorkLogWarning(
                                ErrorCode.WORKLOG_NO_PERIOD_FOR_DATE.getCode(),
                                "Não há período de contrato para esta data",
                                0)); // RN-107
                return Optional.empty();
            }
            if (!period.get().acceptsWorkLogs()) {
                errors.add(
                        new WorkLogWarning(
                                ErrorCode.WORKLOG_LOCKED.getCode(),
                                "Registro pertence a período fechado",
                                0)); // RN-121
                return Optional.empty();
            }
            return period;
        } catch (BusinessRuleException violation) {
            errors.add(toWarning(violation));
            return Optional.empty();
        }
    }

    /** Executa uma verificação acumulando o erro em vez de interromper (CE-17). */
    private void collect(List<WorkLogWarning> errors, Runnable check) {
        try {
            check.run();
        } catch (BusinessRuleException violation) {
            errors.add(toWarning(violation));
        }
    }

    private WorkLogWarning toWarning(BusinessRuleException violation) {
        return new WorkLogWarning(violation.getErrorCode().getCode(), violation.getMessage(), 0);
    }

    private WorkLogConflictResponse toConflict(WorkLog conflict) {
        return mapper.toConflict(conflict, ticketService.getKeyById(conflict.getTicketId()));
    }
}
