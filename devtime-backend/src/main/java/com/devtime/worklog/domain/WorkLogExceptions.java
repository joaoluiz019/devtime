package com.devtime.worklog.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exceções de regra da feature 008 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra que a origina — o que
 * mantém código, mensagem e detalhes coerentes entre si.
 *
 * <p>§19.1 e CP-18: <b>nenhum detalhe carrega {@code description}</b>. O texto é livre e pode
 * conter dado pessoal de terceiros; ele apareceria na resposta de erro e, por consequência, em log
 * de proxy.
 */
public final class WorkLogExceptions {

    private WorkLogExceptions() {}

    /** RN-101: hora sem ticket é hora sem explicação. */
    public static BusinessRuleException ticketRequired() {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_TICKET_REQUIRED,
                Map.of("field", "ticketId"),
                "Ticket é obrigatório para registrar horas");
    }

    /**
     * RN-102: sobreposição com outra sessão do mesmo usuário.
     *
     * <p>Os detalhes carregam o registro conflitante porque "já existe um registro neste intervalo"
     * só é acionável se o usuário conseguir chegar até ele (§21.2, {@code dt-overlap-warning}).
     */
    public static BusinessRuleException overlap(
            UUID conflictingId, Instant conflictStart, Instant conflictEnd) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("conflictingWorkLogId", conflictingId);
        details.put("conflictStartedAt", conflictStart.toString());
        details.put("conflictEndedAt", conflictEnd.toString());
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_OVERLAP,
                details,
                "Já existe um registro de horas neste intervalo");
    }

    /** RN-103: sessão acima de 24h é sempre erro de digitação ou cronômetro esquecido. */
    public static BusinessRuleException sessionTooLong(int grossMinutes) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_SESSION_TOO_LONG,
                Map.of("grossMinutes", grossMinutes, "maxMinutes", 1440),
                "A sessão não pode ultrapassar 24 horas");
    }

    /** RN-104: categoria inexistente, de outro tenant ou inativa. */
    public static BusinessRuleException categoryInvalid() {
        return new WorkLogValidationException(
                ErrorCode.CATEGORY_INVALID_OR_INACTIVE,
                Map.of("field", "categoryId"),
                "Categoria inválida ou inativa");
    }

    /** RN-105 / RN-158: 3 a 2.000 caracteres após aparar as bordas. */
    public static BusinessRuleException descriptionInvalid(int length) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_DESCRIPTION_INVALID,
                Map.of("field", "description", "length", length, "min", 3, "max", 2000),
                "Descrição obrigatória (mínimo 3 caracteres)");
    }

    /** RN-107: a data de trabalho não pertence a nenhum período do contrato. */
    public static BusinessRuleException noPeriodForDate(LocalDate workDate) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_NO_PERIOD_FOR_DATE,
                Map.of("workDate", workDate.toString()),
                "Não há período de contrato para esta data");
    }

    /** RN-114 / INV-WKL-01. */
    public static BusinessRuleException rangeInvalid() {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_RANGE_INVALID,
                Map.of("field", "endedAt"),
                "A hora final deve ser posterior à inicial");
    }

    /**
     * RN-115 / INV-WKL-02.
     *
     * <p>OB-05: uma sessão de 10 minutos com {@code roundingMinutes = 15} cai aqui. É
     * contraintuitivo e correto — a alternativa seria arredondar para cima, cobrando 15 minutos por
     * 10 trabalhados (PR-03). Por isso os detalhes devolvem o valor antes e depois do
     * arredondamento: sem eles, o usuário não teria como entender a rejeição.
     */
    public static BusinessRuleException netMinutesInvalid(int netMinutes, int beforeRounding) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_NET_MINUTES_INVALID,
                Map.of("netMinutes", netMinutes, "netMinutesBeforeRounding", beforeRounding),
                "O tempo líquido deve ser maior que zero");
    }

    /** RN-116 / RN-157 / INV-WKL-04. */
    public static BusinessRuleException pausedMinutesInvalid(int pausedMinutes, int grossMinutes) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_PAUSED_MINUTES_INVALID,
                Map.of("pausedMinutes", pausedMinutes, "grossMinutes", grossMinutes),
                "Tempo de pausa inválido");
    }

    /** RN-117: não se registra hora fora da vigência contratual. */
    public static BusinessRuleException outsideContractValidity(
            LocalDate startDate, LocalDate endDate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("contractStartDate", startDate.toString());
        details.put("contractEndDate", endDate == null ? null : endDate.toString());
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_OUTSIDE_CONTRACT_VALIDITY,
                details,
                "Data fora da vigência do contrato");
    }

    /** RN-118: tolerância de 2 minutos para relógio adiantado (RS-09). */
    public static BusinessRuleException endedInFuture(Instant endedAt, Instant limit) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_ENDED_IN_FUTURE,
                Map.of("endedAt", endedAt.toString(), "maxEndedAt", limit.toString()),
                "Não é possível registrar horas no futuro");
    }

    /** RN-119: padrão conservador; {@code allowFutureWorkLogs} libera. */
    public static BusinessRuleException futureDateNotAllowed(LocalDate workDate, LocalDate today) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_FUTURE_DATE_NOT_ALLOWED,
                Map.of("workDate", workDate.toString(), "today", today.toString()),
                "Registro com data futura não permitido");
    }

    /** RN-120: além da janela, o lançamento exige {@code ADMIN} ou {@code OWNER}. */
    public static BusinessRuleException retroactiveLimit(LocalDate workDate, int limitDays) {
        return new WorkLogValidationException(
                ErrorCode.WORKLOG_RETROACTIVE_LIMIT,
                Map.of("workDate", workDate.toString(), "retroactiveLimitDays", limitDays),
                "Fora da janela de lançamento retroativo");
    }

    /** RN-121 / INV-WKL-07: a correção exige reabertura formal e auditada do período. */
    public static BusinessRuleException locked(UUID contractPeriodId) {
        return new WorkLogConflictException(
                ErrorCode.WORKLOG_LOCKED,
                Map.of("contractPeriodId", contractPeriodId, "suggestedAction", "REOPEN_PERIOD"),
                "Registro pertence a período fechado");
    }

    /** RN-124: mover horas para um período fechado alteraria um relatório já emitido. */
    public static BusinessRuleException periodTransferBlocked(UUID targetPeriodId) {
        return new WorkLogConflictException(
                ErrorCode.WORKLOG_PERIOD_TRANSFER_BLOCKED,
                Map.of("targetPeriodId", targetPeriodId),
                "Período de destino está fechado");
    }

    /** RN-109 / RN-126: tentativa de alterar campo imutável. */
    public static BusinessRuleException immutable(String field) {
        return BusinessRuleException.immutableField(field);
    }

    /**
     * RN-231: {@code OveragePolicy = BLOCK} e o registro estouraria o saldo.
     *
     * <p>RN-234: o registro <b>não</b> é dividido automaticamente. Os detalhes informam quanto
     * ainda cabe, para que a decisão de reduzir o tempo, marcar como não faturável ou pedir ajuste
     * seja do usuário — nunca do sistema (PR-03).
     */
    public static BusinessRuleException balanceInsufficient(
            int availableMinutes, int consumedMinutes, int requestedMinutes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("availableMinutes", availableMinutes);
        details.put("consumedMinutes", consumedMinutes);
        details.put("requestedMinutes", requestedMinutes);
        details.put("remainingMinutes", availableMinutes - consumedMinutes);
        return new WorkLogValidationException(
                ErrorCode.PERIOD_BALANCE_INSUFFICIENT, details, "Saldo insuficiente no contrato");
    }

    /** RN-102 a RN-120: violações que produzem {@code 422}. */
    public static final class WorkLogValidationException extends BusinessRuleException {
        private WorkLogValidationException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }

    /** RN-121, RN-124: violações que produzem {@code 409}. */
    public static final class WorkLogConflictException extends BusinessRuleException {
        private WorkLogConflictException(
                ErrorCode code, Map<String, Object> details, String message) {
            super(code, details, message);
        }
    }
}
