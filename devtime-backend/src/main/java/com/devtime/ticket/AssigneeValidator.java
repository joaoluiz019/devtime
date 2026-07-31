package com.devtime.ticket;

import com.devtime.tenant.MembershipService;
import com.devtime.ticket.domain.TicketExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responsável do ticket (RN-304).
 *
 * <p>Consome {@link MembershipService}, a interface pública de {@code 002-users} — nunca o
 * repositório de memberships (BR-002).
 *
 * <p>Usuário inexistente, de outro tenant, suspenso e removido produzem a <b>mesma</b> resposta
 * ({@code DEVTIME-2304}): distinguir revelaria a existência de contas fora do tenant.
 */
@Component
@RequiredArgsConstructor
public class AssigneeValidator {

    private final MembershipService membershipService;

    /**
     * @param assigneeId responsável informado; {@code null} é válido — FA-01 permite ticket sem
     *     responsável
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2304} / {@code 422}
     */
    public void assertAssignable(UUID assigneeId) {
        if (assigneeId == null) {
            return;
        }
        if (!membershipService.isActiveMember(assigneeId)) {
            throw TicketExceptions.assigneeInvalid(); // RN-304
        }
    }
}
