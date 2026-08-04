package com.devtime.report;

import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.SnapshotService;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.ContractResponses.PeriodReportRef;
import com.devtime.report.domain.ReportExceptions;
import com.devtime.report.domain.ReportSource;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decide de onde os números de um período vêm: snapshot ou cálculo ao vivo (RN-701, RN-702, §6.1).
 *
 * <p><b>É o único ponto de decisão da feature</b>, e isso é deliberado (R-02). O modo de falha que
 * esta classe existe para impedir é silencioso: um caminho que sirva período fechado do banco ao
 * vivo funciona, devolve números que parecem certos, e só se revela quando alguém altera um dado e
 * o "documento definitivo" muda. Espalhar a decisão por cinco serviços criaria cinco lugares onde
 * ela pode divergir, e apenas um precisaria errar.
 *
 * <p>Matriz de §6.1:
 *
 * <table>
 *   <caption>Fonte por estado do período</caption>
 *   <tr><th>Estado</th><th>Fonte</th><th>Marcação</th></tr>
 *   <tr><td>{@code CLOSED}</td><td>snapshot</td><td>definitivo, determinístico (RN-708)</td></tr>
 *   <tr><td>{@code REOPENED}</td><td>ao vivo</td><td>parcial + aviso de reabertura</td></tr>
 *   <tr><td>{@code OPEN}, {@code CLOSING}</td><td>ao vivo</td><td>parcial</td></tr>
 *   <tr><td>{@code SCHEDULED}</td><td>—</td><td>{@code DEVTIME-3002}</td></tr>
 * </table>
 *
 * <p>{@code CLOSING} não aparece em §6.1 e é tratado como aberto: o fechamento é uma transação em
 * curso, o snapshot ainda não existe (é o passo 4 de RN-241) e servir o período como definitivo
 * antes de o passo 5 confirmar produziria um documento que o fechamento ainda pode desfazer.
 */
@Component
@RequiredArgsConstructor
public class ReportDataResolver {

    private final ContractPeriodService periodService;
    private final BalanceService balanceService;
    private final SnapshotService snapshotService;

    /**
     * @throws com.devtime.shared.error.EntityNotFoundException período inexistente ou de outro
     *     tenant — {@code 404}, nunca {@code 403} (ART-024, BR-047)
     */
    public PeriodDataSource resolve(UUID periodId) {
        PeriodReportRef period = periodService.getReportRef(periodId);

        if (!period.isStarted()) {
            // §6.1: um ciclo que não começou não tem o que relatar.
            throw ReportExceptions.periodNotStarted();
        }

        if (period.isClosed()) {
            // RN-701 e CP-01: exclusivamente do snapshot. Nenhum caminho alternativo — nem em
            // caso de erro —, porque um fallback para o cálculo ao vivo é exatamente o defeito
            // que R-02 descreve, e ele apareceria só quando o dado divergisse.
            String payload =
                    snapshotService
                            .payloadForReport(periodId)
                            .orElseThrow(() -> missingSnapshot(periodId));
            return new FromSnapshot(period, payload);
        }

        // RN-702: aberto, em fechamento ou reaberto — cálculo ao vivo, sempre parcial.
        return new FromLive(period, balanceService.getBalance(periodId));
    }

    /**
     * Período {@code CLOSED} sem snapshot é defeito de integridade, não caso de negócio.
     *
     * <p>O passo 4 de RN-241 grava o snapshot <b>antes</b> de o passo 5 marcar o período como
     * fechado, e os sete passos são uma transação: a combinação não deveria existir. Falha alto
     * (CG-06) e vira {@code 500 DEVTIME-9001} sem vazar detalhe — degradar para o cálculo ao vivo
     * devolveria um documento que se apresenta como definitivo e não é.
     */
    private IllegalStateException missingSnapshot(UUID periodId) {
        return new IllegalStateException("Período fechado sem snapshot: " + periodId);
    }

    /** Fonte resolvida. Selada: §6.1 tem duas saídas, e a terceira é uma exceção. */
    public sealed interface PeriodDataSource {

        PeriodReportRef period();

        ReportSource source();

        /** RP-02 e INV-RPT-03: só o snapshot é definitivo. */
        boolean isPartial();
    }

    /** Período fechado: payload congelado (RN-701). */
    public record FromSnapshot(PeriodReportRef period, String payload) implements PeriodDataSource {

        @Override
        public ReportSource source() {
            return ReportSource.SNAPSHOT;
        }

        @Override
        public boolean isPartial() {
            return false;
        }
    }

    /** Período aberto, em fechamento ou reaberto: agregação sobre as tabelas (RN-702). */
    public record FromLive(PeriodReportRef period, PeriodBalanceResponse balance)
            implements PeriodDataSource {

        @Override
        public ReportSource source() {
            return ReportSource.LIVE;
        }

        @Override
        public boolean isPartial() {
            return true;
        }

        /** FA-03 e CA-04: maior que zero adiciona o aviso de reabertura. */
        public int reopenCount() {
            return balance.reopenCount();
        }
    }
}
