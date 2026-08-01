package com.devtime.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.worklog.domain.WorkLogInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tabela normativa de sobreposição (RN-102, §6.2 de specs/008-worklogs) — T-008-04.
 *
 * <p><b>Escrita antes do código</b> (SQ-02). Esta suíte é o oráculo da regra mais crítica da
 * feature: um erro de comparação ({@code <=} em vez de {@code <}) não é percebido em revisão e
 * produz superfaturamento silencioso — a mesma hora cobrada duas vezes (RP-01). Escrevê-la depois
 * significaria confirmar o comportamento do código, que é exatamente o que está sob suspeita.
 *
 * <p>Os intervalos são <b>semi-abertos</b> {@code [início, fim)} (BR-148): sessões que se tocam
 * exatamente são permitidas, porque encerrar uma tarefa às 11:00 e começar a seguinte às 11:00 é o
 * caso mais comum do mundo real.
 */
class WorkLogOverlapRuleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    /**
     * Os nove casos da tabela normativa de §6.2, na ordem em que ela os apresenta.
     *
     * <p>A sessão existente é sempre 09:00–11:00.
     */
    @ParameterizedTest(name = "[{index}] existente 09:00-11:00 × nova {0}-{1} ⇒ sobrepõe={2} ({3})")
    @CsvSource({
        "11:00, 12:00, false, 'tocam-se exatamente; intervalo semi-aberto'",
        "13:00, 14:00, false, 'sem interseção'",
        "08:00, 09:00, false, 'toca pelo início'",
        "09:30, 10:30, true,  'contida'",
        "10:00, 12:00, true,  'parcial à direita'",
        "08:00, 10:00, true,  'parcial à esquerda'",
        "08:00, 15:00, true,  'envolve'",
        "09:00, 11:00, true,  'idêntica'",
        "10:59, 11:01, true,  'sobreposição de 1 minuto'"
    })
    @DisplayName("RN-102: a tabela normativa de sobreposição da §6.2 é reproduzida nos 9 casos")
    void normativeOverlapTable(String start, String end, boolean expected, String reason) {
        WorkLogInterval existing = interval("09:00", "11:00");
        WorkLogInterval candidate = interval(start, end);

        assertThat(candidate.overlaps(existing)).as(reason).isEqualTo(expected);
        // A relação é simétrica por definição: A sobrepõe B se e somente se B sobrepõe A. Sem
        // isso, o resultado dependeria de qual registro chegou primeiro ao banco.
        assertThat(existing.overlaps(candidate)).as("simetria — " + reason).isEqualTo(expected);
    }

    @Nested
    @DisplayName("RN-102: bordas da comparação estrita")
    class StrictComparisonEdges {

        @Test
        @DisplayName("RN-102: fim de A igual ao início de B não sobrepõe (CX-06)")
        void touchingIsAllowed() {
            assertThat(interval("09:00", "11:00").overlaps(interval("11:00", "13:00"))).isFalse();
        }

        @Test
        @DisplayName("RN-102: um único minuto de interseção já sobrepõe (CX-07)")
        void oneMinuteOverlapIsRejected() {
            assertThat(interval("09:00", "11:00").overlaps(interval("10:59", "11:01"))).isTrue();
        }

        @Test
        @DisplayName("RN-102: um único segundo de interseção já sobrepõe")
        void oneSecondOverlapIsRejected() {
            WorkLogInterval existing =
                    new WorkLogInterval(instant("09:00"), instant("11:00").plusSeconds(1));
            assertThat(existing.overlaps(interval("11:00", "12:00"))).isTrue();
        }

        @Test
        @DisplayName("RN-108: sessão que atravessa a meia-noite é um intervalo único e contínuo")
        void midnightCrossingIsASingleInterval() {
            WorkLogInterval night =
                    new WorkLogInterval(
                            DAY.atTime(22, 0).toInstant(ZoneOffset.UTC),
                            DAY.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC));
            WorkLogInterval nextMorning =
                    new WorkLogInterval(
                            DAY.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC),
                            DAY.plusDays(1).atTime(2, 0).toInstant(ZoneOffset.UTC));

            assertThat(night.overlaps(nextMorning))
                    .as("a sessão não é dividida em dois dias (CP-08), então a interseção existe")
                    .isTrue();
        }
    }

    private static WorkLogInterval interval(String start, String end) {
        return new WorkLogInterval(instant(start), instant(end));
    }

    private static Instant instant(String time) {
        return DAY.atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
    }
}
