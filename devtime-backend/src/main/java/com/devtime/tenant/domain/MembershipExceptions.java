package com.devtime.tenant.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Exceções de regra do vínculo (BR-063).
 *
 * <p>Vive em {@code tenant} porque a guarda de transição pertence a quem é dono da entidade: quem
 * consome a interface pública não deveria precisar conhecer os estados internos do {@code
 * Membership} para saber que a operação é inválida.
 */
public final class MembershipExceptions {

    private MembershipExceptions() {}

    /** CP-09 / §11.1 de spec 001: só um vínculo {@code INVITED} pode ser ativado por aceite. */
    public static BusinessRuleException notInvited(String currentStatus) {
        return new MembershipNotInvitedException(currentStatus);
    }

    /** CP-09. */
    public static final class MembershipNotInvitedException extends BusinessRuleException {
        private MembershipNotInvitedException(String currentStatus) {
            super(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    Map.of("from", currentStatus, "to", MembershipStatus.ACTIVE.name()),
                    "Vínculo não está em INVITED");
        }
    }
}
