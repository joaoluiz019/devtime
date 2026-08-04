package com.devtime.contract.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exceções de regra da feature 004 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra que a origina.
 */
public final class ContractExceptions {

    private ContractExceptions() {}

    /** ME-04 / EX-09: a resposta inclui as transições possíveis a partir do estado atual. */
    public static BusinessRuleException invalidTransition(
            ContractStatus from, ContractStatus to, Set<ContractStatus> available) {
        return new InvalidStateTransitionException(from, to, available);
    }

    /** RN-202 / INV-CTR-02. */
    public static BusinessRuleException monthlyMinutesRequired() {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_MONTHLY_MINUTES_INVALID,
                "monthlyMinutes",
                "Contrato mensal exige quantidade de horas entre 1 e 44.640");
    }

    /** INV-CTR-03: {@code HOURLY_OPEN} não aceita saldo nem rollover. */
    public static BusinessRuleException typeIncoherent(String detail) {
        return new InvalidContractTypeException(ErrorCode.CONTRACT_TYPE_INCOHERENT, "type", detail);
    }

    /** INV-CTR-04. */
    public static BusinessRuleException rolloverCapRequired() {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_ROLLOVER_CAP_REQUIRED,
                "rolloverCapMinutes",
                "A política CAPPED exige teto de transporte");
    }

    /** RN-203. */
    public static BusinessRuleException billingDayInvalid(int billingDay) {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_BILLING_DAY_INVALID,
                "billingDay",
                "O dia de faturamento deve estar entre 1 e 28, recebido " + billingDay);
    }

    /** RN-204 / INV-CTR-05. */
    public static BusinessRuleException dateRangeInvalid() {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_DATE_RANGE_INVALID,
                "endDate",
                "A data final deve ser posterior ou igual à inicial");
    }

    /** RN-205: exclusão permitida apenas em {@code DRAFT}. */
    public static BusinessRuleException deleteRestricted(ContractStatus status) {
        return new ContractDeleteRestrictedException(status);
    }

    /** RN-206 / RN-011: {@code type} é imutável fora de {@code DRAFT}. */
    public static BusinessRuleException immutableType() {
        return BusinessRuleException.immutableField("type");
    }

    /** RN-207: a alteração atingiria um período fechado. */
    public static BusinessRuleException changeAffectsClosedPeriod() {
        return new ContractChangeGuardException(
                ErrorCode.CONTRACT_CHANGE_AFFECTS_CLOSED_PERIOD,
                "Alteração afetaria período fechado");
    }

    /** RN-207: alterar o período aberto exige confirmação explícita (CE-CT-02). */
    public static BusinessRuleException currentPeriodChangeNotConfirmed() {
        return new ContractChangeGuardException(
                ErrorCode.CONTRACT_CHANGE_AFFECTS_CLOSED_PERIOD,
                "Alterar o pacote do período aberto exige applyToCurrentPeriod = true");
    }

    /** RN-208: o ciclo não pode mudar com horas lançadas no período aberto. */
    public static BusinessRuleException billingDayLocked() {
        return new ContractChangeGuardException(
                ErrorCode.CONTRACT_BILLING_DAY_LOCKED,
                "Não é possível alterar o ciclo com horas lançadas no período aberto");
    }

    /** contracts.md §8.1: campos obrigatórios do tipo ausentes na ativação. */
    public static BusinessRuleException activationIncomplete(String detail) {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_ACTIVATION_INCOMPLETE, "type", detail);
    }

    /** contracts.md §8.4: {@code endDate} inválida no encerramento. */
    public static BusinessRuleException endDateInvalid(LocalDate endDate) {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_END_DATE_INVALID,
                "endDate",
                "Data de término inválida: " + endDate);
    }

    /**
     * contracts.md §8.2/§8.4: há cronômetro rodando em ticket do contrato.
     *
     * <p>Os identificadores acompanham o erro porque §8.2 exige que a resposta liste os
     * cronômetros: "existe cronômetro ativo" não diz a quem pedir para encerrá-lo. {@code PAUSED}
     * conta como ativo pela mesma razão de RN-240 — o trabalho não terminou, apenas parou.
     */
    public static BusinessRuleException contractHasActiveTimer(java.util.List<UUID> timerIds) {
        return new ContractActiveTimerException(timerIds);
    }

    /** RN-215: justificativa obrigatória com no mínimo 10 caracteres. */
    public static BusinessRuleException justificationRequired() {
        return new InvalidContractTypeException(
                ErrorCode.JUSTIFICATION_REQUIRED,
                "reason",
                "A justificativa deve ter no mínimo 10 caracteres");
    }

    /**
     * RN-306: contrato {@code ENDED} ou {@code CANCELLED} não aceita registro de horas.
     *
     * <p>Existe aqui, e não apenas em {@code TicketExceptions}, porque a regra é do contrato:
     * quando {@code 008} e {@code 009} perguntam por {@code getWorkLogRef}, quem responde é a
     * feature dona do estado.
     */
    public static BusinessRuleException notAcceptingWork(String status) {
        return new InvalidContractTypeException(
                ErrorCode.CONTRACT_NOT_ACCEPTING_WORK,
                "contractId",
                "Contrato em " + status + " não aceita registros de horas");
    }

    /** RN-216: falha de contiguidade — corrupção estrutural, não erro do usuário. */
    public static BusinessRuleException contiguityViolation(
            UUID contractId, LocalDate expected, LocalDate found) {
        return new PeriodContiguityViolationException(contractId, expected, found);
    }

    /** ME-04. */
    public static final class InvalidStateTransitionException extends BusinessRuleException {
        private InvalidStateTransitionException(
                ContractStatus from, ContractStatus to, Set<ContractStatus> available) {
            super(
                    from.isTerminal()
                            ? ErrorCode.TERMINAL_STATE
                            : ErrorCode.INVALID_STATE_TRANSITION,
                    Map.of(
                            "from",
                            from.name(),
                            "to",
                            to.name(),
                            "availableTransitions",
                            available.stream().map(Enum::name).toList()),
                    "Transição inválida: " + from + " → " + to);
        }
    }

    /** RN-202, RN-203, RN-204, RN-206. */
    public static final class InvalidContractTypeException extends BusinessRuleException {
        private InvalidContractTypeException(ErrorCode code, String field, String message) {
            super(code, Map.of("field", field), message);
        }
    }

    /** RN-205. */
    public static final class ContractDeleteRestrictedException extends BusinessRuleException {
        private ContractDeleteRestrictedException(ContractStatus status) {
            super(
                    ErrorCode.CONTRACT_DELETE_RESTRICTED,
                    Map.of("status", status.name()),
                    "Contrato fora de DRAFT não pode ser excluído; use encerrar ou cancelar");
        }
    }

    /** contracts.md §8.2/§8.4. */
    public static final class ContractActiveTimerException extends BusinessRuleException {
        private ContractActiveTimerException(java.util.List<UUID> timerIds) {
            super(
                    ErrorCode.CONTRACT_HAS_ACTIVE_TIMER,
                    Map.of("activeTimerIds", timerIds),
                    "Existe cronômetro ativo no contrato");
        }
    }

    /** RN-207 e RN-208. */
    public static final class ContractChangeGuardException extends BusinessRuleException {
        private ContractChangeGuardException(ErrorCode code, String message) {
            super(code, Map.of(), message);
        }
    }

    /**
     * RN-216.
     *
     * <p>Responde {@code DEVTIME-9001} / {@code 500}: a violação de contiguidade não é erro do
     * usuário nem condição recuperável, é corrupção estrutural detectada antes de persistir. A spec
     * §12 sugere {@code DEVTIME-9002}, código já atribuído a limite de requisições por ADR-017 §
     * tabela e ADR-045 RL-04 — e ErrorCode proíbe reaproveitar um código com outro significado.
     */
    public static final class PeriodContiguityViolationException extends BusinessRuleException {
        private PeriodContiguityViolationException(
                UUID contractId, LocalDate expected, LocalDate found) {
            super(
                    ErrorCode.UNEXPECTED,
                    Map.of("contractId", contractId),
                    "Falha de contiguidade no contrato "
                            + contractId
                            + ": esperado início em "
                            + expected
                            + ", obtido "
                            + found);
        }
    }
}
