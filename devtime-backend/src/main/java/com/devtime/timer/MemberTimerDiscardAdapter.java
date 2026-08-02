package com.devtime.timer;

import com.devtime.tenant.MemberRemovalPorts.TimerDiscardSource;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementação de {@link TimerDiscardSource} (RN-460).
 *
 * <p>Adaptador fino sobre {@code TimerService.discardForUser}, que já existia para o mesmo efeito:
 * a regra de descarte — incluindo o cronômetro em {@code PAUSED} (CX-04) e o registro do tempo
 * descartado na trilha — continua em um único lugar, dentro da feature dona do cronômetro.
 */
@Component
@RequiredArgsConstructor
public class MemberTimerDiscardAdapter implements TimerDiscardSource {

    private final TimerService timerService;

    @Override
    public int discardTimersOf(UUID userId) {
        return timerService.discardForUser(userId);
    }
}
