package com.devtime.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Relógio de teste parado, que só avança quando o teste manda.
 *
 * <p>BR-205 continua valendo: nenhum teste usa relógio real, e o instante inicial é sempre {@link
 * FixedClockTestConfiguration#FIXED_INSTANT}, de modo que toda asserção de igualdade exata sobre
 * {@code createdAt} e afins permanece verdadeira sem alteração.
 *
 * <p>O que muda é o que um relógio congelado tornava impossível verificar: o cronômetro existe para
 * medir tempo decorrido, e com {@code Clock.fixed} pausa e retomada caíam no <b>mesmo instante</b>
 * — violando {@code ck_timer_pauses_range} ({@code resumed_at > paused_at}) e impedindo qualquer
 * asserção sobre {@code pausedMinutes}. A alternativa seria relaxar a restrição do banco para
 * aceitar pausa de duração zero, o que trocaria uma limitação de teste por uma invariante mais
 * fraca em produção.
 *
 * <p>{@link #reset()} é chamado antes de cada teste: o contexto Spring é compartilhado, e um avanço
 * vazado de um teste para outro produziria falha dependente da ordem de execução.
 */
public class MutableTestClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableTestClock(Instant initial, ZoneId zone) {
        this.instant = initial;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return new MutableTestClock(instant, other);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /** Avança o relógio. Nenhum teste retrocede: o tempo do sistema não retrocede. */
    public void advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("o relógio de teste não retrocede");
        }
        instant = instant.plus(amount);
    }

    /** Volta ao instante de referência entre testes. */
    public void reset() {
        instant = FixedClockTestConfiguration.FIXED_INSTANT;
    }
}
