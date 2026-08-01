package com.devtime.timer;

import com.devtime.timer.domain.Timer;
import com.devtime.timer.domain.TimerExceptions;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Unicidade do cronômetro ativo por usuário (RN-150, INV-TMR-01).
 *
 * <p>O limite é por <b>pessoa</b>, não por organização (CE-13, CX-01): alguém que participa de dois
 * tenants continua sendo uma pessoa só, e uma pessoa não trabalha em duas coisas ao mesmo tempo — a
 * mesma premissa de RN-102.
 *
 * <p>A garantia tem duas camadas. Esta política produz a mensagem acionável ({@code DEVTIME-2150},
 * com o cronômetro existente e em qual organização ele está); o índice único parcial {@code
 * uq_timers_active_user}, criado sem {@code tenant_id}, é a barreira contra corrida entre duas
 * requisições simultâneas (SG-03, SG-04). Diferentemente de RN-102 em {@code 008}, aqui a
 * constraint de banco é viável: {@code Timer} não usa exclusão lógica.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActiveTimerPolicy {

    private final TimerRepository repository;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2150} / {@code 409}
     */
    public void assertNoActiveTimer(UUID userId) {
        findActive(userId)
                .ifPresent(
                        active -> {
                            log.info(
                                    "RN-150 violada userId={} activeTimerId={} activeTenantId={}",
                                    userId,
                                    active.getId(),
                                    active.getTenantId());
                            throw TimerExceptions.alreadyActive(
                                    active.getId(), active.getTenantId());
                        });
    }

    /** Cronômetro ativo do usuário em qualquer tenant, sem lançar. */
    public Optional<Timer> findActive(UUID userId) {
        return repository.findActiveByUser(userId);
    }
}
