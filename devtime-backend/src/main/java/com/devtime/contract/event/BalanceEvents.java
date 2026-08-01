package com.devtime.contract.event;

import com.devtime.shared.event.DomainEvent;
import java.util.UUID;

/**
 * Eventos do banco de horas — feature 011 (spec §15).
 *
 * <p>BR-180/BR-181: {@code record} imutável carregando apenas identificadores. Uma entidade dentro
 * do evento chegaria destacada da sessão ao consumidor de {@code AFTER_COMMIT}.
 */
public final class BalanceEvents {

    private BalanceEvents() {}

    /**
     * RN-602: o consumo do período mudou e os limiares precisam ser reavaliados.
     *
     * <p>Consumido por {@code 013-notifications} após o commit. O delta viaja junto porque um
     * limiar só é cruzado em uma direção: sem ele, o avaliador teria de comparar com um estado
     * anterior que já não existe.
     */
    public record ConsumptionChangedEvent(UUID periodId, UUID contractId, int billableDelta)
            implements DomainEvent {}

    /** Ajuste manual aplicado; reavalia limiares e alimenta métricas. */
    public record AdjustmentAppliedEvent(
            UUID adjustmentId, UUID periodId, UUID contractId, int minutes)
            implements DomainEvent {}

    /** RN-241 passo 7: publicado após o commit; invalida caches de relatório em {@code 012}. */
    public record PeriodClosedEvent(
            UUID periodId, UUID contractId, int carriedOutMinutes, String snapshotChecksum)
            implements DomainEvent {}

    /** RN-242: a reabertura altera um relatório já emitido — os consumidores precisam saber. */
    public record PeriodReopenedEvent(UUID periodId, UUID contractId, int reopenCount)
            implements DomainEvent {}
}
