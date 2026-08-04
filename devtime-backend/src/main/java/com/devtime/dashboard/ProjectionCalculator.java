package com.devtime.dashboard;

import com.devtime.dashboard.domain.ProjectionStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Projeção de consumo do período (§6.3 de specs/010, entities.md §6.7).
 *
 * <p>BR-066: cálculo puro. As fórmulas são as de entities.md — {@code burnRate = consumedMinutes /
 * elapsedWorkDays} e {@code projectedConsumption = burnRate × totalWorkDays} — e nada de saldo é
 * recalculado aqui: {@code consumedMinutes} e {@code availableMinutes} chegam de {@code
 * BalanceService} (INV-DSH-01).
 *
 * <p><b>Dia útil é de segunda a sexta.</b> O sistema não modela feriados em lugar nenhum — não
 * existe tabela nem campo de calendário —, e inventar um seria criar regra de negócio (IA-01). A
 * consequência aceita é que uma semana com feriado projeta um pouco acima do real, o que erra para
 * o lado do alerta e não do silêncio.
 */
@Component
public class ProjectionCalculator {

    /**
     * Mínimo de dias úteis decorridos para que a projeção seja exibida (§6.3, OB-04).
     *
     * <p>Com um único dia decorrido, {@code burnRate × totalWorkDays} projeta cerca de 20× esse
     * dia: matematicamente correto, praticamente inútil e alarmante. Três dias é o mínimo para a
     * média ter alguma estabilidade. O número é uma decisão de apresentação da spec, registrada
     * para poder ser revista com dados do beta.
     */
    static final int MIN_ELAPSED_WORK_DAYS = 3;

    /** Margem além da qual o estouro deixa de ser "risco" e passa a ser provável (§6.3). */
    private static final double AT_RISK_MARGIN = 1.1;

    /** Projeção e sua leitura, devolvidas juntas porque a segunda depende da primeira. */
    public record Projection(int projectedConsumedMinutes, ProjectionStatus status) {

        static Projection notApplicable() {
            return new Projection(0, ProjectionStatus.NOT_APPLICABLE);
        }
    }

    /**
     * @param periodStart primeiro dia do período de apuração, inclusive
     * @param periodEnd último dia, inclusive
     * @param today data corrente no fuso do tenant (RN-009)
     * @param consumedMinutes consumo apurado por {@code 011}
     * @param availableMinutes saldo disponível apurado por {@code 011}
     */
    public Projection calculate(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate today,
            int consumedMinutes,
            int availableMinutes) {
        if (periodStart == null || periodEnd == null) {
            return Projection.notApplicable();
        }
        // CX-05 / CE-10: HOURLY_OPEN não tem teto; projetar contra um limite inexistente não
        // significa nada.
        if (availableMinutes <= 0) {
            return Projection.notApplicable();
        }

        LocalDate elapsedUntil = today.isAfter(periodEnd) ? periodEnd : today;
        int elapsedWorkDays = workDaysBetween(periodStart, elapsedUntil);
        if (elapsedWorkDays < MIN_ELAPSED_WORK_DAYS) {
            return Projection.notApplicable(); // CX-04
        }

        int totalWorkDays = workDaysBetween(periodStart, periodEnd);
        double burnRate = (double) consumedMinutes / elapsedWorkDays;
        // Truncamento, nunca arredondamento para cima (BR-145): a projeção não deve inflar o número
        // que decide se o usuário toma uma ação.
        int projected = (int) (burnRate * totalWorkDays);

        if (projected <= availableMinutes) {
            return new Projection(projected, ProjectionStatus.WITHIN_LIMIT);
        }
        if (projected <= availableMinutes * AT_RISK_MARGIN) {
            return new Projection(projected, ProjectionStatus.AT_RISK);
        }
        return new Projection(projected, ProjectionStatus.WILL_EXCEED);
    }

    /** Dias úteis do intervalo fechado {@code [start, end]} (BR-149). */
    int workDaysBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return 0;
        }
        int workDays = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY
                    && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workDays++;
            }
        }
        return workDays;
    }
}
