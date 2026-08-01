package com.devtime.worklog;

import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCalendarResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogTotalsResponse;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Calendário e totais agregados (worklogs.md §7 e §8).
 *
 * <p>O agrupamento é sempre por {@code workDate}, que já é a data local do tenant (RN-108, RN-009).
 * Agrupar pelo instante em UTC colocaria uma sessão das 22h no dia seguinte para tenants a leste de
 * Greenwich — o registro apareceria no dia errado do calendário, que é justamente o erro que RN-009
 * existe para impedir.
 */
public interface WorkLogAggregationService {

    /** P22: totais por dia no intervalo, no fuso do tenant. */
    WorkLogCalendarResponse calendar(LocalDate from, LocalDate to, UUID userId);

    /**
     * P21: totais dos <b>mesmos filtros</b> da listagem.
     *
     * <p>Os filtros são os mesmos objetos por exigência de coerência: o total exibido no topo da
     * tela precisa somar exatamente as linhas mostradas abaixo dele, inclusive sob o escopo de
     * dados de {@code MEMBER} (SG-03).
     */
    WorkLogTotalsResponse totals(WorkLogFilter filter);
}
