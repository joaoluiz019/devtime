package com.devtime.ticket;

import com.devtime.ticket.domain.TicketExceptions;
import org.springframework.stereotype.Component;

/**
 * Motivo do impedimento (state-machines.md §4.7).
 *
 * <p>Um ticket bloqueado sem motivo é indistinguível de um ticket esquecido: o mínimo de 5
 * caracteres existe para que a linha do tempo registre <b>o que</b> impede, não apenas <i>que</i>
 * algo impede. Quatro caracteres são rejeitados (CX-20).
 */
@Component
public class BlockReasonValidator {

    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 500;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2314} / {@code 422}
     */
    public String requireReason(String blockReason) {
        String trimmed = blockReason == null ? "" : blockReason.strip();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw TicketExceptions.blockReasonRequired();
        }
        return trimmed;
    }
}
