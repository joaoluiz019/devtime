package com.devtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.shared.error.BusinessRuleException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Matriz completa de transições do contrato (state-machines.md §4.5).
 *
 * <p>T-004-40, escrita antes da implementação (SQ-02). Cada célula da matriz origem × destino é
 * verificada, aceitando ou rejeitando — uma matriz testada só nas células permitidas não prova que
 * as proibidas estão fechadas.
 */
class ContractStateMachineTest {

    private final ContractStateMachine stateMachine = new ContractStateMachine();

    /** Matriz de state-machines.md §4.5, na mesma ordem do documento. */
    private static final Set<String> ALLOWED =
            Set.of(
                    "DRAFT->ACTIVE",
                    "DRAFT->CANCELLED",
                    "ACTIVE->SUSPENDED",
                    "ACTIVE->ENDED",
                    "ACTIVE->CANCELLED",
                    "SUSPENDED->ACTIVE",
                    "SUSPENDED->ENDED",
                    "SUSPENDED->CANCELLED");

    @Test
    @DisplayName("ME-04: toda célula da matriz origem × destino tem o mesmo veredito do documento")
    void shouldMatchDocumentedTransitionMatrix() {
        for (ContractStatus from : ContractStatus.values()) {
            for (ContractStatus to : ContractStatus.values()) {
                boolean expected = ALLOWED.contains(from + "->" + to);
                assertThat(stateMachine.canTransition(from, to))
                        .as("transição %s → %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = ContractStatus.class,
            names = {"ENDED", "CANCELLED"})
    @DisplayName("CE-15: estados terminais não aceitam nenhuma transição de saída")
    void terminalStatesShouldRejectEveryTransition(ContractStatus terminal) {
        for (ContractStatus target : ContractStatus.values()) {
            assertThat(stateMachine.canTransition(terminal, target)).isFalse();
        }
    }

    @Test
    @DisplayName("RN-206: DRAFT → ACTIVE é a única entrada em vigência")
    void onlyDraftMayBecomeActiveFromNonSuspended() {
        assertThat(stateMachine.canTransition(ContractStatus.DRAFT, ContractStatus.ACTIVE))
                .isTrue();
        assertThat(stateMachine.canTransition(ContractStatus.ENDED, ContractStatus.ACTIVE))
                .isFalse();
    }

    @Test
    @DisplayName("DEVTIME-2010: transição inválida lança exceção com as transições disponíveis")
    void shouldRejectInvalidTransitionWithAvailableOnes() {
        assertThatThrownBy(
                        () ->
                                stateMachine.assertCanTransition(
                                        ContractStatus.DRAFT, ContractStatus.SUSPENDED))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2010");
    }

    @Test
    @DisplayName("ME-06: availableTransitions reflete o estado atual")
    void shouldExposeAvailableTransitions() {
        assertThat(stateMachine.availableTransitions(ContractStatus.DRAFT))
                .containsExactlyInAnyOrder(ContractStatus.ACTIVE, ContractStatus.CANCELLED);
        assertThat(stateMachine.availableTransitions(ContractStatus.ACTIVE))
                .containsExactlyInAnyOrder(
                        ContractStatus.SUSPENDED, ContractStatus.ENDED, ContractStatus.CANCELLED);
        assertThat(stateMachine.availableTransitions(ContractStatus.ENDED)).isEmpty();
    }
}
