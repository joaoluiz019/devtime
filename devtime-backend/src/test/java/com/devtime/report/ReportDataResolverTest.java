package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.SnapshotService;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.ContractResponses.PeriodReportRef;
import com.devtime.report.ReportDataResolver.FromLive;
import com.devtime.report.ReportDataResolver.FromSnapshot;
import com.devtime.report.ReportDataResolver.PeriodDataSource;
import com.devtime.report.domain.ReportSource;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * T-012-04 — a suíte que precede {@code ReportDataResolver} (RN-701, RN-702, §6.1).
 *
 * <p>É o único teste que expõe o modo de falha de R-02: servir período fechado do banco ao vivo. O
 * defeito é silencioso — o relatório funciona e os números parecem certos, até alguém alterar um
 * dado e o "documento definitivo" mudar entre duas gerações. Por isso as asserções aqui não são só
 * sobre o que o resolvedor devolve, mas sobre <b>quem ele não chamou</b>: com período fechado,
 * {@code BalanceService} nunca é tocado.
 *
 * <p><b>Pendência declarada (T-012-38).</b> Estes testes exercitam o caminho de snapshot contra um
 * payload de fixture. A reexecução contra o fechamento real de {@code 011} pertence a S10: um teste
 * de snapshot que nunca viu um fechamento de verdade não prova RN-701 de ponta a ponta.
 */
@ExtendWith(MockitoExtension.class)
class ReportDataResolverTest {

    private static final UUID PERIOD_ID = UUID.fromString("0192f3a4-0000-7000-8000-00000000c101");
    private static final UUID CONTRACT_ID = UUID.fromString("0192f3a4-0000-7000-8000-00000000c001");

    /**
     * O payload de um período fechado, com valores que <b>divergem</b> dos que as tabelas
     * devolveriam. É a divergência que torna o teste capaz de detectar a falha: se o resolvedor
     * consultar o banco, os números do relatório serão os do banco, não estes.
     */
    private static final String FROZEN_PAYLOAD =
            """
            {"schemaVersion":2,"snapshotAt":"2026-08-01T12:15:00Z",
             "client":{"name":"Acme Corporation"},
             "totals":{"consumedMinutes":2900}}
            """;

    @Mock private ContractPeriodService periodService;
    @Mock private BalanceService balanceService;
    @Mock private SnapshotService snapshotService;

    @InjectMocks private ReportDataResolver resolver;

    @Test
    @DisplayName("RN-701 / CA-01: período CLOSED é servido do snapshot, e o banco não é consultado")
    void closedPeriodComesFromSnapshot() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("CLOSED"));
        when(snapshotService.payloadForReport(PERIOD_ID)).thenReturn(Optional.of(FROZEN_PAYLOAD));

        PeriodDataSource resolved = resolver.resolve(PERIOD_ID);

        assertThat(resolved).isInstanceOf(FromSnapshot.class);
        assertThat(resolved.source()).isEqualTo(ReportSource.SNAPSHOT);
        assertThat(((FromSnapshot) resolved).payload()).isEqualTo(FROZEN_PAYLOAD);

        // CP-01 e R-02: o caminho do cálculo ao vivo nem sequer é tocado. Esta é a asserção que
        // detecta o defeito silencioso — um fallback "por segurança" passaria em toda asserção de
        // conteúdo e falharia aqui.
        verify(balanceService, never()).getBalance(any());
    }

    @Test
    @DisplayName("RN-701 / CA-02: alteração cadastral posterior não alcança o período fechado")
    void closedPeriodIgnoresLaterChanges() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("CLOSED"));
        when(snapshotService.payloadForReport(PERIOD_ID)).thenReturn(Optional.of(FROZEN_PAYLOAD));

        // O cliente foi renomeado depois do fechamento (CX-02). A única fonte que o resolvedor
        // oferece continua sendo o payload, onde o nome antigo está congelado.
        FromSnapshot resolved = (FromSnapshot) resolver.resolve(PERIOD_ID);

        assertThat(resolved.payload()).contains("Acme Corporation");
        assertThat(resolved.isPartial())
                .as("documento definitivo: nada nele ainda pode mudar")
                .isFalse();
    }

    @Test
    @DisplayName("RN-702 / CA-03: período OPEN é calculado ao vivo e marcado como parcial")
    void openPeriodIsLiveAndPartial() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("OPEN"));
        when(balanceService.getBalance(PERIOD_ID)).thenReturn(balance(0));

        PeriodDataSource resolved = resolver.resolve(PERIOD_ID);

        assertThat(resolved).isInstanceOf(FromLive.class);
        assertThat(resolved.source()).isEqualTo(ReportSource.LIVE);
        assertThat(resolved.isPartial()).isTrue();
        verify(snapshotService, never()).payloadForReport(any());
    }

    @Test
    @DisplayName("FA-03 / CA-04: período REOPENED é parcial e carrega a contagem de reaberturas")
    void reopenedPeriodCarriesReopenCount() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("REOPENED"));
        when(balanceService.getBalance(PERIOD_ID)).thenReturn(balance(2));

        FromLive resolved = (FromLive) resolver.resolve(PERIOD_ID);

        assertThat(resolved.isPartial()).isTrue();
        assertThat(resolved.reopenCount())
                .as("é o que dispara o aviso de reabertura em todas as saídas")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("§6.1: período em CLOSING é tratado como aberto — o snapshot ainda não existe")
    void closingPeriodIsTreatedAsOpen() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("CLOSING"));
        when(balanceService.getBalance(PERIOD_ID)).thenReturn(balance(0));

        PeriodDataSource resolved = resolver.resolve(PERIOD_ID);

        assertThat(resolved.source()).isEqualTo(ReportSource.LIVE);
        assertThat(resolved.isPartial()).isTrue();
    }

    @Test
    @DisplayName("§6.1 / DEVTIME-3002: período SCHEDULED não produz relatório")
    void scheduledPeriodIsUnavailable() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("SCHEDULED"));

        assertThatThrownBy(() -> resolver.resolve(PERIOD_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_PERIOD_NOT_STARTED);
    }

    @Test
    @DisplayName("R-02: período fechado sem snapshot falha alto — nunca degrada para o banco")
    void closedPeriodWithoutSnapshotFailsLoud() {
        when(periodService.getReportRef(PERIOD_ID)).thenReturn(period("CLOSED"));
        when(snapshotService.payloadForReport(PERIOD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(PERIOD_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(balanceService, never()).getBalance(any());
    }

    /**
     * {@code isClosed} e {@code isStarted} chegam <b>decididos</b> por {@code 004}: o relatório não
     * conhece {@code PeriodStatus} (ART-065), e o teste reproduz o contrato da interface pública.
     */
    private PeriodReportRef period(String status) {
        return new PeriodReportRef(
                PERIOD_ID,
                CONTRACT_ID,
                7,
                "2026-07",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                status,
                "CLOSED".equals(status),
                !"SCHEDULED".equals(status),
                0,
                "BRL");
    }

    private PeriodBalanceResponse balance(int reopenCount) {
        return new PeriodBalanceResponse(
                PERIOD_ID,
                CONTRACT_ID,
                7,
                "2026-07",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                "OPEN",
                2400,
                300,
                60,
                2760,
                2310,
                195,
                450,
                0,
                new BigDecimal("83.70"),
                true,
                reopenCount,
                "BRL");
    }
}
