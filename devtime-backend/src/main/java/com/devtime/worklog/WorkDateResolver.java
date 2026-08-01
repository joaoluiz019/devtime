package com.devtime.worklog;

import com.devtime.shared.time.TenantClock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolução de {@code workDate} a partir de {@code startedAt} (RN-108, RN-009).
 *
 * <p>A data é <b>a data local de início no fuso do tenant</b>, nunca a de término e nunca em UTC.
 * Duas consequências, ambas deliberadas:
 *
 * <ul>
 *   <li><b>Sessão que atravessa a meia-noite pertence ao dia de início</b> (CX-01, CP-08). Uma
 *       sessão das 22h às 01h30 é uma única narrativa de trabalho: dividi-la produziria dois
 *       registros com descrições duplicadas, e atribuí-la ao dia seguinte deslocaria o trabalho de
 *       uma noite de terça para quarta.
 *   <li><b>Sessão que atravessa a virada do período pertence ao período do início</b> (CE-02),
 *       porque o período é resolvido por {@code workDate}.
 * </ul>
 *
 * <p>Horário de verão (CX-03, CX-04) não exige tratamento especial: os instantes são UTC, o que
 * elimina a ambiguidade da hora repetida e a inexistência da hora pulada. A conversão para data
 * local usa as regras de transição da IANA, aplicadas por {@code ZoneId}.
 */
@Component
@RequiredArgsConstructor
public class WorkDateResolver {

    private final TenantClock clock;

    /** RN-108: data de calendário do <b>início</b> da sessão, no fuso do tenant. */
    public LocalDate resolve(Instant startedAt) {
        return clock.toTenantDate(startedAt);
    }

    /** Hoje no fuso do tenant — referência de RN-119 e RN-120. */
    public LocalDate today() {
        return clock.today();
    }
}
