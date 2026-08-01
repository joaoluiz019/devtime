package com.devtime.timer.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Sessão de trabalho em andamento, persistida no servidor (entities.md §6.14).
 *
 * <p><b>O estado vive no servidor</b> (RN-151, RN-167). O cliente apenas renderiza o tempo
 * decorrido a partir de {@code startedAt}, {@code lastResumedAt} e {@code
 * accumulatedActiveSeconds}. Fechar o navegador, trocar de máquina, perder a conexão ou reiniciar o
 * backend não perde nada — e é essa decisão que elimina RP-02, a perda de tempo trabalhado.
 *
 * <p><b>{@code accumulatedActiveSeconds} não é a fonte da duração do work log.</b> Ele existe
 * exclusivamente para exibição em tempo real e pode divergir em até um minuto do valor canônico por
 * truncamento (§6.2 de specs/009). O valor que vira work log é sempre {@code gross − paused}
 * (RN-111): persistir a partir do acumulado produziria um número diferente do que as regras de
 * {@code 008} calculariam — dois valores para a mesma sessão.
 */
@Entity
@Table(name = "timers")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Timer extends TenantScopedEntity {

    /** 🔒 OWN-05: o cronômetro pertence <b>exclusivamente</b> ao seu usuário. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** RN-161: alterável enquanto {@code RUNNING} ou {@code PAUSED}. */
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /** RN-161: alterável durante a execução. */
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private TimerStatus status;

    /** 🔒 RN-152: sempre do servidor. Alterá-lo seria reescrever quando o trabalho começou. */
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** Início do trecho ativo corrente; movido a cada retomada (RN-156). */
    @Column(name = "last_resumed_at", nullable = false)
    private Instant lastResumedAt;

    /** Somente exibição (§6.2). Nunca alimenta o cálculo do work log. */
    @Column(name = "accumulated_active_seconds", nullable = false)
    private int accumulatedActiveSeconds;

    /** 💾 RN-156: soma das pausas concluídas, em minutos inteiros (ART-034). */
    @Column(name = "paused_minutes", nullable = false)
    private int pausedMinutes;

    /** RN-158: opcional ao iniciar, <b>obrigatória</b> ao encerrar. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "billable", nullable = false)
    private boolean billable;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    /** INV-TMR-04: preenchido no encerramento bem-sucedido; INV-TMR-05: nulo em descarte. */
    @Column(name = "work_log_id")
    private UUID workLogId;

    /** RN-163: garante que o alerta de cronômetro longo seja emitido <b>uma única vez</b>. */
    @Column(name = "long_running_notified_at")
    private Instant longRunningNotifiedAt;

    /** RN-150 / INV-TMR-01. */
    public boolean isActive() {
        return status.isActive();
    }

    /**
     * §6.2: {@code accumulated + (RUNNING ? now − lastResumedAt : 0)}.
     *
     * <p>Congelado em {@code PAUSED} — é o que "pausado" significa.
     */
    public int elapsedSeconds(Instant now) {
        if (status != TimerStatus.RUNNING) {
            return accumulatedActiveSeconds;
        }
        return accumulatedActiveSeconds + (int) Duration.between(lastResumedAt, now).toSeconds();
    }

    /**
     * §6.2: {@code now − startedAt}, incluindo o tempo pausado.
     *
     * <p>É o critério de RN-164 (CX-07): um cronômetro esquecido em pausa há 16 horas continua
     * sendo um cronômetro esquecido.
     */
    public long grossElapsedSeconds(Instant now) {
        return Duration.between(startedAt, now).toSeconds();
    }
}
