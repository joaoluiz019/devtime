package com.devtime.timer.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Intervalo de pausa de um cronômetro (entities.md §6.15).
 *
 * <p>Existe apenas para calcular {@code timer.pausedMinutes}. §19.1: <b>o histórico de pausas não é
 * exposto em nenhum relatório e não acompanha {@code TIMER_VIEW_ANY}</b> — ele revela o ritmo de
 * trabalho de uma pessoa em granularidade fina (quando parou, por quanto tempo, quantas vezes), o
 * dado mais íntimo que o produto coleta.
 *
 * <p>{@code reason} é opcional e nunca obrigatório, pela mesma razão: exigir justificativa para
 * cada pausa transformaria o produto em ferramenta de vigilância.
 */
@Entity
@Table(name = "timer_pauses")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class TimerPause extends TenantScopedEntity {

    @Column(name = "timer_id", nullable = false, updatable = false)
    private UUID timerId;

    @Column(name = "paused_at", nullable = false, updatable = false)
    private Instant pausedAt;

    /** Nulo enquanto a pausa está aberta (INV-TMR-02). */
    @Column(name = "resumed_at")
    private Instant resumedAt;

    /** Calculado no fechamento da pausa; nulo enquanto ela está aberta. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "reason", length = 200)
    private String reason;

    public boolean isOpen() {
        return resumedAt == null;
    }
}
