package com.devtime.timer;

import com.devtime.shared.persistence.SoftDeleteRepository;
import com.devtime.timer.domain.TimerPause;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistência de {@link TimerPause} (spec 009 §25). */
@Repository
public interface TimerPauseRepository extends SoftDeleteRepository<TimerPause> {

    /**
     * INV-TMR-02 / INV-TMR-03: a pausa aberta do cronômetro, se houver.
     *
     * <p>O índice único parcial {@code uq_timer_pauses_open} garante que exista no máximo uma; esta
     * consulta é o caminho normal de leitura, e a constraint é a barreira contra corrida.
     */
    @Query("SELECT p FROM TimerPause p WHERE p.timerId = :timerId AND p.resumedAt IS NULL")
    Optional<TimerPause> findOpenByTimer(@Param("timerId") UUID timerId);

    /**
     * Soma das pausas <b>concluídas</b>, em segundos.
     *
     * <p>Recalculada a cada retomada em vez de acumulada por incremento: com poucas dezenas de
     * pausas por cronômetro, o custo é irrelevante, e a soma real elimina a classe de defeito em
     * que um incremento perdido produz {@code pausedMinutes} menor que o tempo efetivamente parado
     * — que seria cobrado do cliente.
     */
    @Query(
            """
            SELECT COALESCE(SUM(p.durationSeconds), 0) FROM TimerPause p
             WHERE p.timerId = :timerId
               AND p.resumedAt IS NOT NULL
            """)
    long sumDurationSecondsByTimer(@Param("timerId") UUID timerId);
}
