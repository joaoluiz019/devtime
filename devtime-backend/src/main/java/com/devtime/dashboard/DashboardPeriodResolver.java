package com.devtime.dashboard;

import com.devtime.dashboard.domain.DashboardExceptions;
import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.shared.time.DateRange;
import com.devtime.shared.time.TenantClock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve o intervalo consultado, no fuso do tenant (§22.3 de specs/010).
 *
 * <p>RN-009 e CP-15: a resolução usa {@link TenantClock#today()}, nunca a data do servidor nem a do
 * navegador. "Hoje" precisa ser o dia local do tenant — agregar em UTC deslocaria o dia para metade
 * dos usuários, que é exatamente o erro que RN-009 existe para impedir.
 */
@Component
@RequiredArgsConstructor
public class DashboardPeriodResolver {

    private final TenantClock clock;

    /**
     * Intervalo fechado {@code [from, to]} correspondente ao período pedido.
     *
     * <p><b>{@code CURRENT_PERIOD} é o mês corrente do calendário</b>, no fuso do tenant. O painel
     * é do tenant inteiro e cada contrato tem o seu próprio ciclo de apuração ({@code billingDay},
     * RN-211): não existe um período de apuração único a que "o período corrente" pudesse se
     * referir. O mês do calendário é a leitura compatível com o exemplo normativo de reports.md
     * §10.1 ({@code 2026-07-01} a {@code 2026-07-31}) e é a única estável para um tenant com
     * contratos de dias de faturamento distintos. O saldo por contrato continua vindo do período de
     * apuração real de cada um, via {@code BalanceService} (INV-DSH-01).
     *
     * @throws DashboardExceptions.DashboardValidationException quando {@code CUSTOM} chega sem
     *     {@code from}/{@code to} ou com {@code to} anterior a {@code from}
     * @throws DashboardExceptions.DateRangeTooLargeException RN-705: acima de 366 dias
     */
    public DateRange resolve(DashboardPeriodType type, LocalDate from, LocalDate to) {
        LocalDate today = clock.today();
        return switch (type == null ? DashboardPeriodType.CURRENT_PERIOD : type) {
            case CURRENT_PERIOD -> new DateRange(today.withDayOfMonth(1), endOfMonth(today));
            // Intervalo fechado (BR-149): "últimos 7 dias" inclui hoje, logo começa em hoje − 6.
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today);
            case LAST_30_DAYS -> new DateRange(today.minusDays(29), today);
            case CUSTOM -> custom(from, to);
        };
    }

    private DateRange custom(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw DashboardExceptions.customRangeIncomplete();
        }
        if (to.isBefore(from)) {
            throw DashboardExceptions.invertedRange();
        }
        DateRange range = new DateRange(from, to);
        // RN-705: 366 dias é aceito, 367 é rejeitado (CX-18). O limite conta ambas as extremidades.
        if (range.lengthInDays() > DashboardExceptions.MAX_CUSTOM_RANGE_DAYS) {
            throw DashboardExceptions.dateRangeTooLarge(range.lengthInDays());
        }
        return range;
    }

    private LocalDate endOfMonth(LocalDate reference) {
        return reference.withDayOfMonth(reference.lengthOfMonth());
    }

    /**
     * Chave estável do intervalo, para compor a chave do cache de gráficos (SG-07).
     *
     * <p>Deriva das datas resolvidas e não do tipo pedido: {@code LAST_30_DAYS} designa um
     * intervalo diferente a cada dia, e usar o nome do tipo faria o cache de ontem responder hoje.
     */
    public String cacheKeyOf(DateRange range) {
        return range.start() + ":" + range.end();
    }
}
