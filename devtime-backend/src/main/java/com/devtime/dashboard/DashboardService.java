package com.devtime.dashboard;

import com.devtime.dashboard.domain.DashboardPeriodType;
import com.devtime.dashboard.dto.DashboardResponses.DashboardResponse;
import java.time.LocalDate;

/**
 * Painel operacional do tenant (§22.2 de specs/010, §10.1 de reports.md).
 *
 * <p><b>Esta feature é folha no grafo:</b> consome seis features e não é consumida por nenhuma. É o
 * que a torna a candidata mais segura de corte — ela é {@code P1} e consta na ordem de corte de
 * {@code mvp.md} (OB-06). Por isso nenhum método daqui é publicado a outras features.
 *
 * <p><b>Nenhum saldo é recalculado</b> (INV-DSH-01, OB-01): todos os números de contrato vêm de
 * {@code BalanceService}. O maior risco de um painel é reimplementar o cálculo "porque é mais
 * rápido que chamar o serviço", produzindo um segundo número que divergirá do primeiro na primeira
 * mudança de regra.
 */
public interface DashboardService {

    /**
     * Painel completo.
     *
     * @param period tipo de período; nulo aplica {@code CURRENT_PERIOD}
     * @param from início do intervalo personalizado; obrigatório apenas em {@code CUSTOM}
     * @param to fim do intervalo personalizado
     */
    DashboardResponse load(DashboardPeriodType period, LocalDate from, LocalDate to);
}
