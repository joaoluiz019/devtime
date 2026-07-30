package com.devtime.contract;

import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.PeriodPlan;
import com.devtime.contract.domain.PeriodSpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Geração de períodos contíguos (RN-211, RN-212, RN-214, RN-217; spec 004 §6.2).
 *
 * <p>Este é o ponto do sistema em que um erro de borda de calendário produz horas alocadas no
 * período errado — e um relatório errado entregue ao cliente. Daí a classificação de complexidade
 * crítica e a exigência de que a suíte temporal ({@code PeriodGeneratorTest}) existisse antes desta
 * classe (SQ-02).
 *
 * <p>BR-066: cálculo puro, sem acesso a banco. A prévia e a geração real usam este mesmo código, o
 * que torna estrutural a garantia de CA-01 ("a prévia reflete exatamente o que será gerado").
 */
@Component
@RequiredArgsConstructor
public class PeriodGenerator {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ProrationCalculator prorationCalculator;

    /**
     * Gera a partir de {@code spec.startDate}, com {@code sequence} começando em 1.
     *
     * @param maxPeriods limite superior; a geração pode parar antes por {@code endDate} (RN-214)
     */
    public List<PeriodPlan> generate(PeriodSpec spec, int maxPeriods) {
        return build(spec, spec.startDate(), 1, maxPeriods);
    }

    /**
     * Gera os períodos seguintes a um já existente, preservando a contiguidade (INV-PER-03).
     *
     * <p>Usado na retomada de contrato suspenso, em que ciclos podem ter ficado para trás
     * (CE-ME-09), e pela geração automática de {@code S4}.
     */
    public List<PeriodPlan> generateAfter(
            PeriodSpec spec, LocalDate previousEndDate, int previousSequence, int maxPeriods) {
        return build(spec, previousEndDate.plusDays(1), previousSequence + 1, maxPeriods);
    }

    private List<PeriodPlan> build(
            PeriodSpec spec, LocalDate firstStart, int firstSequence, int maxPeriods) {
        List<PeriodPlan> periods = new ArrayList<>();
        LocalDate start = firstStart;
        int sequence = firstSequence;

        while (periods.size() < maxPeriods) {
            if (spec.endDate() != null && start.isAfter(spec.endDate())) {
                break; // RN-214: nada é gerado além do fim da vigência.
            }
            // Passo 2 da §6.2: fim no dia anterior à próxima ocorrência do billingDay.
            LocalDate naturalEnd = nextBillingDay(start, spec.billingDay()).minusDays(1);
            boolean truncated = spec.endDate() != null && !spec.endDate().isAfter(naturalEnd);
            LocalDate end = truncated ? spec.endDate() : naturalEnd;

            int periodDays = daysInclusive(start, end);
            int fullCycleDays = fullCycleDays(start, spec.billingDay());
            boolean partial = periodDays != fullCycleDays;

            periods.add(
                    new PeriodPlan(
                            sequence++,
                            label(start, end),
                            start,
                            end,
                            contractedMinutes(spec, partial, periodDays, fullCycleDays),
                            partial,
                            periodDays,
                            fullCycleDays));

            if (truncated) {
                break; // RN-214: o período truncado é o último.
            }
            // Passo 4: o próximo começa no dia seguinte — é o que garante INV-PER-03.
            start = end.plusDays(1);
        }
        return List.copyOf(periods);
    }

    /** Passos 6 a 8 da §6.2. */
    private int contractedMinutes(
            PeriodSpec spec, boolean partial, int periodDays, int fullCycleDays) {
        if (spec.type() == ContractType.HOURLY_OPEN || spec.monthlyMinutes() == null) {
            return 0; // RN-210: horas abertas não têm teto, logo não têm valor contratado.
        }
        if (!partial || !spec.prorateFirstPeriod()) {
            return spec.monthlyMinutes(); // CX-07: sem rateio é decisão explícita do usuário.
        }
        return prorationCalculator.prorate(
                spec.monthlyMinutes(), periodDays, fullCycleDays); // RN-217
    }

    /**
     * Duração do ciclo cheio ao qual o período pertence.
     *
     * <p>É o denominador do rateio: para {@code startDate = 10/01} e {@code billingDay = 1}, o
     * ciclo cheio é 01/01–31/01 (31 dias), e não os 30 dias contados a partir do início do período
     * — é essa escolha que reproduz o exemplo normativo de 1.703 minutos.
     */
    private int fullCycleDays(LocalDate periodStart, int billingDay) {
        LocalDate cycleStart = currentBillingDay(periodStart, billingDay);
        LocalDate cycleEnd = nextBillingDay(cycleStart, billingDay).minusDays(1);
        return daysInclusive(cycleStart, cycleEnd);
    }

    /**
     * Próxima ocorrência do {@code billingDay} <b>estritamente após</b> a data.
     *
     * <p>{@code withDayOfMonth} é seguro sem verificação porque RN-203 restringe {@code billingDay}
     * a 1–28: todo mês possui esses dias, inclusive fevereiro em ano não bissexto. É exatamente a
     * ambiguidade que a regra elimina (CX-01, CX-02).
     */
    private LocalDate nextBillingDay(LocalDate reference, int billingDay) {
        return reference.getDayOfMonth() < billingDay
                ? reference.withDayOfMonth(billingDay)
                : reference.plusMonths(1).withDayOfMonth(billingDay);
    }

    /** Ocorrência do {@code billingDay} na data ou imediatamente antes dela. */
    private LocalDate currentBillingDay(LocalDate reference, int billingDay) {
        return reference.getDayOfMonth() >= billingDay
                ? reference.withDayOfMonth(billingDay)
                : reference.minusMonths(1).withDayOfMonth(billingDay);
    }

    /** Intervalo de datas fechado {@code [start, end]} (entities.md §7.2). */
    private int daysInclusive(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * Rótulo derivado das datas (entities.md §6.7).
     *
     * <p>Um período contido em um único mês é identificado pelo mês; qualquer outro precisa das
     * duas datas para ser reconhecível — "2026-07" seria enganoso para um ciclo de 15/07 a 14/08.
     */
    private String label(LocalDate start, LocalDate end) {
        boolean sameMonth = start.getYear() == end.getYear() && start.getMonth() == end.getMonth();
        return sameMonth ? start.format(MONTH) : start.format(DAY) + " a " + end.format(DAY);
    }
}
