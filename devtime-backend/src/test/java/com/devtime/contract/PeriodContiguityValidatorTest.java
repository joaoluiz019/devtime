package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.domain.PeriodPlan;
import com.devtime.shared.error.BusinessRuleException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contiguidade e ausência de sobreposição (RN-216, INV-PER-02/03).
 *
 * <p>O validador é a barreira que roda <b>antes</b> de persistir. Testá-lo isoladamente importa
 * porque, se ele falhar em detectar a lacuna, o erro só apareceria como violação de constraint no
 * banco — sem indicar qual sequência divergiu.
 */
class PeriodContiguityValidatorTest {

    private final PeriodContiguityValidator validator = new PeriodContiguityValidator();
    private final UUID contractId = UUID.randomUUID();

    @Test
    @DisplayName("INV-PER-03: sequência contígua a partir de um período existente é aceita")
    void shouldAcceptContiguousSequence() {
        assertThatCode(
                        () ->
                                validator.assertContiguous(
                                        contractId,
                                        LocalDate.of(2026, 1, 31),
                                        List.of(
                                                plan(
                                                        2,
                                                        LocalDate.of(2026, 2, 1),
                                                        LocalDate.of(2026, 2, 28)),
                                                plan(
                                                        3,
                                                        LocalDate.of(2026, 3, 1),
                                                        LocalDate.of(2026, 3, 31)))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INV-PER-03: sem período anterior, a sequência começa livremente")
    void shouldAcceptFirstSequenceWithoutPrevious() {
        assertThatCode(
                        () ->
                                validator.assertContiguous(
                                        contractId,
                                        null,
                                        List.of(
                                                plan(
                                                        1,
                                                        LocalDate.of(2026, 1, 10),
                                                        LocalDate.of(2026, 1, 31)))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-216: lacuna entre o período anterior e o novo é falha crítica")
    void shouldRejectGap() {
        assertThatThrownBy(
                        () ->
                                validator.assertContiguous(
                                        contractId,
                                        LocalDate.of(2026, 1, 31),
                                        List.of(
                                                plan(
                                                        2,
                                                        LocalDate.of(2026, 2, 5),
                                                        LocalDate.of(2026, 3, 4)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("contiguidade");
    }

    @Test
    @DisplayName("INV-PER-02: sobreposição entre dois períodos gerados é rejeitada")
    void shouldRejectOverlapBetweenGeneratedPeriods() {
        assertThatThrownBy(
                        () ->
                                validator.assertContiguous(
                                        contractId,
                                        null,
                                        List.of(
                                                plan(
                                                        1,
                                                        LocalDate.of(2026, 1, 1),
                                                        LocalDate.of(2026, 1, 31)),
                                                plan(
                                                        2,
                                                        LocalDate.of(2026, 1, 20),
                                                        LocalDate.of(2026, 2, 19)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("INV-PER-04: período com fim anterior ao início é rejeitado")
    void shouldRejectInvertedPeriod() {
        assertThatThrownBy(
                        () ->
                                validator.assertContiguous(
                                        contractId,
                                        null,
                                        List.of(
                                                plan(
                                                        1,
                                                        LocalDate.of(2026, 2, 10),
                                                        LocalDate.of(2026, 2, 1)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    private PeriodPlan plan(int sequence, LocalDate start, LocalDate end) {
        return new PeriodPlan(sequence, "teste", start, end, 0, false, 1, 1);
    }
}
