package com.devtime.tenant.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;

/**
 * Exceções de organização e vínculo (spec 002 §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra.
 */
public final class TenantExceptions {

    private TenantExceptions() {}

    /** RN-455 / INV-TEN-02: a operação deixaria o tenant sem OWNER ativo. */
    public static BusinessRuleException lastOwner() {
        return new LastOwnerException();
    }

    /** RN-456 / OWN-06: ninguém altera o próprio papel. */
    public static BusinessRuleException selfRoleChange() {
        return new SelfRoleChangeException();
    }

    /** Nota ¹ de permissions.md §7: {@code ADMIN} não age sobre {@code OWNER}. */
    public static BusinessRuleException adminOverOwner() {
        return new AdminOverOwnerException();
    }

    /** {@code users.md} §7.2: já existe vínculo ativo ou convite pendente para o e-mail. */
    public static BusinessRuleException alreadyMember() {
        return new AlreadyMemberException();
    }

    /**
     * users.md §6.2: {@code timerAutoAbandonMinutes} não supera {@code timerLongRunningMinutes}.
     */
    public static BusinessRuleException timerThresholdsInconsistent(
            int longRunningMinutes, int autoAbandonMinutes) {
        return new TimerThresholdsInconsistentException(longRunningMinutes, autoAbandonMinutes);
    }

    /** RN-113: valor de arredondamento fora do conjunto suportado. */
    public static BusinessRuleException roundingNotSupported(int minutes, List<Integer> allowed) {
        return new RoundingNotSupportedException(minutes, allowed);
    }

    /**
     * SG-04: cancelamento com senha incorreta.
     *
     * <p>Reusa {@code DEVTIME-1011} de {@code authentication.md} §8, que já significa exatamente
     * "senha atual incorreta". A spec 002 §17.2 indica {@code DEVTIME-1003}/401, mas aquele código
     * já está publicado com outro significado — "usuário sem organização ativa" — e ART-113 proíbe
     * reaproveitá-lo. Divergência reportada.
     */
    public static BusinessRuleException incorrectPassword() {
        return new IncorrectPasswordException();
    }

    /** §6.2: chave de configuração fora da faixa permitida. */
    public static BusinessRuleException settingOutOfRange(
            String key, Object rejectedValue, String allowed) {
        return new SettingOutOfRangeException(key, rejectedValue, allowed);
    }

    /**
     * §4.1 de state-machines.md: cancelamento com período em {@code CLOSING}.
     *
     * <p>Bloqueia até o fechamento concluir ou ser revertido pelo {@code StuckClosingJob} (CX-12):
     * cancelar no meio de um fechamento deixaria um período travado sem quem o destrave.
     */
    public static BusinessRuleException cancellationBlockedByClosing() {
        return new CancellationBlockedException();
    }

    /** {@code DEVTIME-2455} / 409. */
    public static final class LastOwnerException extends BusinessRuleException {
        private LastOwnerException() {
            super(
                    ErrorCode.LAST_OWNER_REQUIRED,
                    Map.of("hint", "Promova outro membro a OWNER antes desta operação"),
                    "A organização deve ter ao menos um proprietário ativo");
        }
    }

    /** {@code DEVTIME-2456} / 403. */
    public static final class SelfRoleChangeException extends BusinessRuleException {
        private SelfRoleChangeException() {
            super(ErrorCode.SELF_ROLE_CHANGE, Map.of(), "Você não pode alterar o próprio papel");
        }
    }

    /** {@code DEVTIME-1104} / 403. */
    public static final class AdminOverOwnerException extends BusinessRuleException {
        private AdminOverOwnerException() {
            super(ErrorCode.ADMIN_OVER_OWNER, Map.of(), "Ação não permitida sobre um proprietário");
        }
    }

    /** {@code DEVTIME-2459} / 409. */
    public static final class AlreadyMemberException extends BusinessRuleException {
        private AlreadyMemberException() {
            super(
                    ErrorCode.ALREADY_MEMBER,
                    Map.of(),
                    "Este e-mail já participa da organização ou possui convite pendente");
        }
    }

    /** {@code DEVTIME-1011} / 422. */
    public static final class IncorrectPasswordException extends BusinessRuleException {
        private IncorrectPasswordException() {
            super(ErrorCode.CURRENT_PASSWORD_INCORRECT, Map.of(), "Senha incorreta");
        }
    }

    /** {@code DEVTIME-2020} / 422. */
    public static final class TimerThresholdsInconsistentException extends BusinessRuleException {
        private TimerThresholdsInconsistentException(int longRunning, int autoAbandon) {
            super(
                    ErrorCode.TIMER_THRESHOLDS_INCONSISTENT,
                    Map.of(
                            "timerLongRunningMinutes", longRunning,
                            "timerAutoAbandonMinutes", autoAbandon),
                    "O limiar de abandono deve ser maior que o de alerta");
        }
    }

    /** {@code DEVTIME-2021} / 422. */
    public static final class RoundingNotSupportedException extends BusinessRuleException {
        private RoundingNotSupportedException(int minutes, List<Integer> allowed) {
            super(
                    ErrorCode.ROUNDING_MINUTES_UNSUPPORTED,
                    Map.of("roundingMinutes", minutes, "allowed", allowed),
                    "Valor de arredondamento não suportado");
        }
    }

    /** {@code DEVTIME-2000} / 400. */
    public static final class SettingOutOfRangeException extends BusinessRuleException {
        private SettingOutOfRangeException(String key, Object rejectedValue, String allowed) {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of(
                            "field",
                            "settings." + key,
                            "rejectedValue",
                            String.valueOf(rejectedValue),
                            "allowed",
                            allowed),
                    "Valor fora da faixa permitida");
        }
    }

    /** {@code DEVTIME-2010} / 409. */
    public static final class CancellationBlockedException extends BusinessRuleException {
        private CancellationBlockedException() {
            super(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    Map.of("blockedBy", "PERIOD_CLOSING"),
                    "Existe período em fechamento; aguarde a conclusão");
        }
    }
}
