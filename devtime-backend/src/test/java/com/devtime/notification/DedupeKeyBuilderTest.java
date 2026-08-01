package com.devtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Chaves de deduplicação (RN-601, RN-603, §6.1 da spec) — T-013-04.
 *
 * <p>A chave é o mecanismo inteiro da feature: um formato divergente entre dois produtores criaria
 * duas chaves para o mesmo evento lógico, e a deduplicação deixaria de funcionar <b>sem que nada
 * falhasse visivelmente</b>. Esta suíte fixa o formato de cada tipo do catálogo.
 */
class DedupeKeyBuilderTest {

    private static final UUID PERIOD = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final UUID CONTRACT = UUID.fromString("0192f3a4-0000-7000-8000-000000000002");
    private static final UUID TIMER = UUID.fromString("0192f3a4-0000-7000-8000-000000000003");
    private static final UUID TICKET = UUID.fromString("0192f3a4-0000-7000-8000-000000000004");
    private static final UUID COMMENT = UUID.fromString("0192f3a4-0000-7000-8000-000000000005");
    private static final UUID USER = UUID.fromString("0192f3a4-0000-7000-8000-000000000006");
    private static final UUID WORK_LOG = UUID.fromString("0192f3a4-0000-7000-8000-000000000007");
    private static final UUID ADJUSTMENT = UUID.fromString("0192f3a4-0000-7000-8000-000000000008");

    private final DedupeKeyBuilder builder = new DedupeKeyBuilder();

    @Test
    @DisplayName("RN-603: a chave de consumo é CONTRACT_USAGE:{periodId}:{threshold}")
    void consumptionKey() {
        assertThat(builder.consumption(PERIOD, 80)).isEqualTo("CONTRACT_USAGE:" + PERIOD + ":80");
    }

    @Test
    @DisplayName("RN-604: o excedente tem chave própria, sem discriminador")
    void overageKey() {
        assertThat(builder.overage(PERIOD)).isEqualTo("CONTRACT_OVERAGE:" + PERIOD);
    }

    @Test
    @DisplayName("§6.1: as chaves de período seguem o catálogo")
    void periodKeys() {
        assertThat(builder.periodClosing(PERIOD)).isEqualTo("PERIOD_CLOSING:" + PERIOD);
        assertThat(builder.periodClosed(PERIOD)).isEqualTo("PERIOD_CLOSED:" + PERIOD);
        assertThat(builder.periodReopened(PERIOD, 2)).isEqualTo("PERIOD_REOPENED:" + PERIOD + ":2");
    }

    @Test
    @DisplayName("§6.1: as chaves de cronômetro seguem o catálogo")
    void timerKeys() {
        assertThat(builder.timerLongRunning(TIMER)).isEqualTo("TIMER_LONG:" + TIMER);
        assertThat(builder.timerAbandoned(TIMER)).isEqualTo("TIMER_ABANDONED:" + TIMER);
        assertThat(builder.timerForceStopped(TIMER)).isEqualTo("TIMER_FORCED:" + TIMER);
    }

    @Test
    @DisplayName("RN-606: a chave de contrato terminando é por contrato")
    void contractEndingKey() {
        assertThat(builder.contractEnding(CONTRACT)).isEqualTo("CONTRACT_ENDING:" + CONTRACT);
    }

    @Test
    @DisplayName("§6.1: as chaves de ticket e comentário incluem o destinatário quando previsto")
    void ticketKeys() {
        assertThat(builder.ticketAssigned(TICKET, USER))
                .isEqualTo("TICKET_ASSIGNED:" + TICKET + ":" + USER);
        assertThat(builder.ticketReopened(TICKET, WORK_LOG))
                .isEqualTo("TICKET_REOPENED:" + TICKET + ":" + WORK_LOG);
        assertThat(builder.ticketCommented(COMMENT, USER))
                .isEqualTo("TICKET_COMMENT:" + COMMENT + ":" + USER);
        assertThat(builder.ticketMentioned(COMMENT, USER))
                .isEqualTo("TICKET_MENTION:" + COMMENT + ":" + USER);
        assertThat(builder.adjustmentApplied(ADJUSTMENT, USER))
                .isEqualTo("ADJUSTMENT:" + ADJUSTMENT + ":" + USER);
    }

    @Nested
    @DisplayName("RN-601: estabilidade e distinção das chaves")
    class KeyStability {

        @ParameterizedTest(name = "limiar {0}")
        @ValueSource(ints = {50, 70, 80, 90, 100, 150})
        @DisplayName("RN-603/CP-05: cada limiar produz uma chave distinta, inclusive acima de 100")
        void eachThresholdHasItsOwnKey(int threshold) {
            // CE-N-03: um limiar de 150% é suportado — a chave é sobre o valor configurado, não
            // sobre uma lista fixa de três.
            assertThat(builder.consumption(PERIOD, threshold))
                    .isEqualTo("CONTRACT_USAGE:" + PERIOD + ":" + threshold)
                    .isNotEqualTo(builder.consumption(PERIOD, threshold + 1));
        }

        @Test
        @DisplayName("RN-601: a mesma entrada produz sempre a mesma chave")
        void keysAreDeterministic() {
            assertThat(builder.consumption(PERIOD, 80)).isEqualTo(builder.consumption(PERIOD, 80));
        }

        @Test
        @DisplayName("CX-08: fechar e refechar produzem a mesma chave; reabrir produz outra")
        void closingIsTheSameFactAndReopeningIsNot() {
            assertThat(builder.periodClosed(PERIOD))
                    .as("o período foi fechado — é sempre o mesmo fato")
                    .isEqualTo(builder.periodClosed(PERIOD));
            assertThat(builder.periodReopened(PERIOD, 1))
                    .as("cada reabertura altera um relatório já entregue — fato novo")
                    .isNotEqualTo(builder.periodReopened(PERIOD, 2));
        }

        @Test
        @DisplayName("RN-601: o limiar e o excedente do mesmo período são chaves diferentes")
        void thresholdAndOverageAreDistinct() {
            // RN-604: "atingiu o teto" e "passou do teto" são fatos distintos.
            assertThat(builder.consumption(PERIOD, 100)).isNotEqualTo(builder.overage(PERIOD));
        }

        @Test
        @DisplayName("§13.4: nenhuma chave excede o limite de 200 caracteres da coluna")
        void keysFitTheColumn() {
            assertThat(builder.ticketCommented(COMMENT, USER).length()).isLessThanOrEqualTo(200);
            assertThat(builder.adjustmentApplied(ADJUSTMENT, USER).length())
                    .isLessThanOrEqualTo(200);
        }
    }
}
