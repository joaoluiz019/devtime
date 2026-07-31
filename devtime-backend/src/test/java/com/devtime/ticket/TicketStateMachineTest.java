package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.ticket.domain.TicketStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Matriz de transição do ticket — as 49 células de state-machines.md §4.7.
 *
 * <p>Suíte escrita <b>antes</b> da máquina (T-007-08). Das 49 células, 22 são válidas e 27
 * proibidas. Escrever a matriz depois testaria as transições que o código implementa, deixando as
 * proibidas sem cobertura — e é justamente uma transição proibida executada por engano que corrompe
 * o histórico ({@code DONE → CANCELLED}).
 *
 * <p>A matriz esperada é transcrita aqui a partir do documento, <b>não</b> importada de {@link
 * TicketStateMachine}: uma suíte que consultasse a própria implementação provaria apenas que ela é
 * consistente consigo mesma.
 */
class TicketStateMachineTest {

    private final TicketStateMachine stateMachine = new TicketStateMachine();

    /** Transcrição literal da matriz de state-machines.md §4.7. */
    private static Map<TicketStatus, Set<TicketStatus>> expectedMatrix() {
        Map<TicketStatus, Set<TicketStatus>> matrix = new EnumMap<>(TicketStatus.class);
        matrix.put(
                TicketStatus.BACKLOG,
                EnumSet.of(TicketStatus.TODO, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.TODO,
                EnumSet.of(TicketStatus.BACKLOG, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.IN_PROGRESS,
                EnumSet.of(
                        TicketStatus.TODO,
                        TicketStatus.BLOCKED,
                        TicketStatus.IN_REVIEW,
                        TicketStatus.DONE,
                        TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.BLOCKED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        matrix.put(
                TicketStatus.IN_REVIEW,
                EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.DONE, TicketStatus.CANCELLED));
        matrix.put(TicketStatus.DONE, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW));
        matrix.put(TicketStatus.CANCELLED, EnumSet.of(TicketStatus.BACKLOG));
        return matrix;
    }

    @TestFactory
    @DisplayName(
            "ME-04: as 49 células da matriz §4.7 são aceitas ou rejeitadas conforme documentado")
    Stream<DynamicTest> shouldCoverEveryCellOfTheMatrix() {
        Map<TicketStatus, Set<TicketStatus>> expected = expectedMatrix();
        return Stream.of(TicketStatus.values())
                .flatMap(
                        from ->
                                Stream.of(TicketStatus.values())
                                        .map(
                                                to ->
                                                        DynamicTest.dynamicTest(
                                                                from + " → " + to,
                                                                () ->
                                                                        assertCell(
                                                                                from,
                                                                                to,
                                                                                expected.get(from)
                                                                                        .contains(
                                                                                                to)))));
    }

    private void assertCell(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(stateMachine.canTransition(from, to))
                .as("célula (%s → %s) da matriz §4.7", from, to)
                .isEqualTo(allowed);
    }

    /**
     * Contagem das células marcadas ✅ na matriz de state-machines.md §4.7.
     *
     * <p>São <b>19</b>: 3 (BACKLOG) + 3 (TODO) + 5 (IN_PROGRESS) + 2 (BLOCKED) + 3 (IN_REVIEW) + 2
     * (DONE) + 1 (CANCELLED). {@code specs/007-tickets/tasks.md} T-007-08 menciona "22 válidas e 27
     * proibidas"; a soma não fecha com a matriz sob nenhuma contagem (49 − 19 = 30, das quais 7 são
     * a diagonal). A matriz de {@code 02-domain/} prevalece por IA-11, e a divergência está
     * reportada no {@code CHANGELOG.md}.
     */
    private static final long VALID_TRANSITIONS = 19;

    @Test
    @DisplayName("§4.7: exatamente 19 das 49 células são transições válidas")
    void shouldDeclareExactlyTheValidTransitionsOfTheMatrix() {
        long valid =
                Stream.of(TicketStatus.values())
                        .flatMap(
                                from ->
                                        Stream.of(TicketStatus.values())
                                                .filter(to -> stateMachine.canTransition(from, to)))
                        .count();
        assertThat(valid).isEqualTo(VALID_TRANSITIONS);
    }

    @Test
    @DisplayName("CP-08: DONE → CANCELLED é rejeitado com DEVTIME-2010")
    void shouldRejectDoneToCancelled() {
        assertThatThrownBy(
                        () ->
                                stateMachine.assertCanTransition(
                                        TicketStatus.DONE, TicketStatus.CANCELLED))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2010");
    }

    @Test
    @DisplayName("EX-09: a rejeição devolve as transições possíveis a partir do estado atual")
    void rejectionShouldCarryAvailableTransitions() {
        assertThatThrownBy(
                        () ->
                                stateMachine.assertCanTransition(
                                        TicketStatus.BLOCKED, TicketStatus.DONE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getDetails())
                .satisfies(
                        details ->
                                assertThat(details)
                                        .containsEntry(
                                                "availableTransitions",
                                                java.util.List.of("CANCELLED", "IN_PROGRESS")));
    }

    @Test
    @DisplayName("CP-08: CANCELLED só reativa para BACKLOG — reativar deve recomeçar o fluxo")
    void cancelledShouldOnlyReactivateToBacklog() {
        assertThat(stateMachine.availableTransitions(TicketStatus.CANCELLED))
                .containsExactly(TicketStatus.BACKLOG);
    }

    @Test
    @DisplayName("§4.7: IN_PROGRESS → BACKLOG é proibido — o trabalho já começou")
    void inProgressShouldNotReturnToBacklog() {
        assertThat(stateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.BACKLOG))
                .isFalse();
    }

    @Test
    @DisplayName("§4.7: não se bloqueia o que não começou — BACKLOG e TODO não vão a BLOCKED")
    void shouldNotBlockWhatHasNotStarted() {
        assertThat(stateMachine.canTransition(TicketStatus.BACKLOG, TicketStatus.BLOCKED))
                .isFalse();
        assertThat(stateMachine.canTransition(TicketStatus.TODO, TicketStatus.BLOCKED)).isFalse();
    }

    @Test
    @DisplayName("ME-06: sem TICKET_TRANSITION, availableTransitions é vazio para qualquer estado")
    void availableTransitionsShouldBeEmptyWithoutPermission() {
        Set<Permission> viewerPermissions = RolePermissions.of(Role.VIEWER);

        assertThat(viewerPermissions).doesNotContain(Permission.TICKET_TRANSITION);
        Stream.of(TicketStatus.values())
                .forEach(
                        status ->
                                assertThat(
                                                stateMachine.availableTransitions(
                                                        status, viewerPermissions))
                                        .as("VIEWER não recebe ação que resultaria em 403")
                                        .isEmpty());
    }

    @Test
    @DisplayName("ME-06: com TICKET_TRANSITION, availableTransitions reflete a matriz do estado")
    void availableTransitionsShouldReflectMatrixWithPermission() {
        assertThat(
                        stateMachine.availableTransitions(
                                TicketStatus.IN_PROGRESS, RolePermissions.of(Role.MEMBER)))
                .containsExactlyInAnyOrder(
                        TicketStatus.TODO,
                        TicketStatus.BLOCKED,
                        TicketStatus.IN_REVIEW,
                        TicketStatus.DONE,
                        TicketStatus.CANCELLED);
    }
}
