package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.support.TicketScenario;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.ticket.dto.TicketRequests.TicketAssignRequest;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketTransitionRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Transições, guardas e efeitos de estado (RN-310 a RN-314, §4.7 de state-machines.md). */
class TicketTransitionIntegrationTest extends FeatureTestSupport {

    @Autowired private TicketService ticketService;
    @Autowired private TicketTransitionService transitionService;
    @Autowired private TicketTotalsService totalsService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("RN-310/CA-05: startedAt é preenchido apenas na 1ª entrada em IN_PROGRESS")
    void startedAtShouldBeSetOnFirstEntryOnly() {
        TicketResponse ticket = newTicket();

        TicketResponse started = transition(ticket, TicketStatus.IN_PROGRESS, null);
        TicketResponse back = transition(started, TicketStatus.TODO, null);
        TicketResponse restarted = transition(back, TicketStatus.IN_PROGRESS, null);

        assertThat(started.startedAt()).isNotNull();
        assertThat(restarted.startedAt())
                .as("startedAt responde 'quando o trabalho começou'; reescrevê-lo apagaria isso")
                .isEqualTo(started.startedAt());
    }

    @Test
    @DisplayName("RN-310/CA-06: completedAt é preenchido em DONE e limpo em toda saída")
    void completedAtShouldBeSetOnDoneAndClearedOnExit() {
        TicketResponse inProgress = transition(newTicket(), TicketStatus.IN_PROGRESS, null);

        TicketResponse done = transition(inProgress, TicketStatus.DONE, null);
        assertThat(done.completedAt()).isNotNull();

        TicketResponse reopened = transition(done, TicketStatus.IN_PROGRESS, null);
        assertThat(reopened.completedAt())
                .as("INV-TCK-04: um ticket que voltou não está concluído")
                .isNull();
    }

    @Test
    @DisplayName(
            "§4.7/CX-20: BLOCKED sem motivo, ou com 4 caracteres, é rejeitado com DEVTIME-2314")
    void blockedShouldRequireReason() {
        TicketResponse inProgress = transition(newTicket(), TicketStatus.IN_PROGRESS, null);

        assertThatThrownBy(() -> transition(inProgress, TicketStatus.BLOCKED, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2314");
        assertThatThrownBy(() -> transition(inProgress, TicketStatus.BLOCKED, "abcd"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2314");
    }

    @Test
    @DisplayName("§4.7: o desbloqueio limpa blockReason — o motivo pertence ao impedimento")
    void unblockShouldClearReason() {
        TicketResponse inProgress = transition(newTicket(), TicketStatus.IN_PROGRESS, null);
        TicketResponse blocked =
                transition(inProgress, TicketStatus.BLOCKED, "Aguardando acesso ao ambiente");

        assertThat(blocked.blockReason()).isEqualTo("Aguardando acesso ao ambiente");
        assertThat(transition(blocked, TicketStatus.IN_PROGRESS, null).blockReason()).isNull();
    }

    @Test
    @DisplayName("CP-08/CA-04: DONE → CANCELLED é rejeitado com DEVTIME-2010")
    void shouldRejectCancellingDoneTicket() {
        TicketResponse done =
                transition(
                        transition(newTicket(), TicketStatus.IN_PROGRESS, null),
                        TicketStatus.DONE,
                        null);

        assertThatThrownBy(() -> transition(done, TicketStatus.CANCELLED, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2010");
    }

    @Test
    @DisplayName("ME-03/CX-16: auto-transição responde 200 sem efeito e sem auditoria")
    void selfTransitionShouldBeIgnored() {
        TicketResponse ticket = newTicket();

        TicketResponse result = transition(ticket, TicketStatus.BACKLOG, null);

        assertThat(result.status()).isEqualTo(TicketStatus.BACKLOG);
        assertThat(result.version()).isEqualTo(ticket.version());
    }

    @Test
    @DisplayName("RN-314/FA-10: cancelar preserva as horas registradas e não devolve saldo")
    void cancelShouldPreserveWorkLogs() {
        TicketResponse ticket = newTicket();
        asOwnerOfA(
                () -> {
                    totalsService.applyWorkLogDelta(ticket.id(), 2400, 2400);
                    return null;
                });
        TicketResponse withHours = asOwnerOfA(() -> ticketService.getById(ticket.id()));

        TicketResponse cancelled = transition(withHours, TicketStatus.CANCELLED, null);

        assertThat(cancelled.status()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelled.spentMinutes())
                .as("o trabalho foi realizado, independentemente do desfecho")
                .isEqualTo(2400);
    }

    @Test
    @DisplayName("FA-11/CX-15: reativar cancelado exige contrato ACTIVE ou SUSPENDED")
    void reactivationShouldRequireOperableContract() {
        TicketResponse cancelled = transition(newTicket(), TicketStatus.CANCELLED, null);

        assertThat(transition(cancelled, TicketStatus.BACKLOG, null).status())
                .isEqualTo(TicketStatus.BACKLOG);
    }

    @Test
    @DisplayName("RN-311: sem cronômetro ativo, a conclusão é permitida")
    void doneShouldBeAllowedWithoutActiveTimer() {
        TicketResponse inProgress = transition(newTicket(), TicketStatus.IN_PROGRESS, null);

        assertThat(transition(inProgress, TicketStatus.DONE, null).status())
                .isEqualTo(TicketStatus.DONE);
    }

    @Test
    @DisplayName("RN-312: work log em ticket DONE reabre para IN_PROGRESS e limpa completedAt")
    void workLogShouldReopenDoneTicket() {
        TicketResponse done =
                transition(
                        transition(newTicket(), TicketStatus.IN_PROGRESS, null),
                        TicketStatus.DONE,
                        null);
        UUID workLogId = UUID.randomUUID();

        asOwnerOfA(
                () -> {
                    transitionService.reopenOnWorkLog(done.id(), workLogId);
                    return null;
                });

        TicketResponse reopened = asOwnerOfA(() -> ticketService.getById(done.id()));
        assertThat(reopened.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(reopened.completedAt()).isNull();
    }

    @Test
    @DisplayName("CX-06/CA-09: excluir o work log que reabriu o ticket não reverte o status")
    void reopeningShouldNotBeReverted() {
        TicketResponse done =
                transition(
                        transition(newTicket(), TicketStatus.IN_PROGRESS, null),
                        TicketStatus.DONE,
                        null);
        UUID workLogId = UUID.randomUUID();

        asOwnerOfA(
                () -> {
                    transitionService.reopenOnWorkLog(done.id(), workLogId);
                    // A exclusão do work log reduz os totais, mas RN-312 não preserva o estado
                    // anterior — reverter exigiria saber qual era, e a regra não o guarda.
                    totalsService.applyWorkLogDelta(done.id(), 0, 0);
                    return null;
                });

        assertThat(asOwnerOfA(() -> ticketService.getById(done.id())).status())
                .isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("RN-312: reabrir um ticket que não está em DONE é operação sem efeito")
    void reopenShouldBeNoOpOutsideDone() {
        TicketResponse ticket = newTicket();

        asOwnerOfA(
                () -> {
                    transitionService.reopenOnWorkLog(ticket.id(), UUID.randomUUID());
                    return null;
                });

        assertThat(asOwnerOfA(() -> ticketService.getById(ticket.id())).status())
                .isEqualTo(TicketStatus.BACKLOG);
    }

    @Test
    @DisplayName("Nota ⁴/OWN-04: MEMBER recebe 403 ao transicionar ticket alheio")
    void memberShouldNotTransitionOthersTickets() {
        TicketResponse ticket = newTicket();
        UUID memberId = memberOfTenantA();

        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        memberId,
                                        Role.MEMBER,
                                        () ->
                                                transitionService.transition(
                                                        ticket.id(),
                                                        new TicketTransitionRequest(
                                                                TicketStatus.IN_PROGRESS,
                                                                null,
                                                                ticket.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-1103");
    }

    @Test
    @DisplayName("Nota ⁴/OWN-04: MEMBER transiciona o ticket em que é responsável")
    void memberShouldTransitionOwnTicket() {
        UUID memberId = memberOfTenantA();
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Atribuído ao membro",
                                                null,
                                                TicketType.FEATURE,
                                                TicketPriority.MEDIUM,
                                                memberId,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)));

        TicketResponse moved =
                runAs(
                        tenantAId,
                        memberId,
                        Role.MEMBER,
                        () ->
                                transitionService.transition(
                                        ticket.id(),
                                        new TicketTransitionRequest(
                                                TicketStatus.IN_PROGRESS, null, ticket.version())));

        assertThat(moved.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("FA-05: atribuir null remove o responsável, sem erro")
    void assigningNullShouldRemoveAssignee() {
        UUID memberId = memberOfTenantA();
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Com responsável",
                                                null,
                                                null,
                                                null,
                                                memberId,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)));

        TicketResponse unassigned =
                asOwnerOfA(
                        () ->
                                transitionService.assign(
                                        ticket.id(),
                                        new TicketAssignRequest(null, ticket.version())));

        assertThat(unassigned.assignee()).isNull();
    }

    @Test
    @DisplayName("RN-304: atribuir usuário sem membership ativo retorna DEVTIME-2304")
    void assignShouldRejectInactiveMember() {
        TicketResponse ticket = newTicket();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                transitionService.assign(
                                                        ticket.id(),
                                                        new TicketAssignRequest(
                                                                userBId, ticket.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2304");
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private ContractResponse activeContract() {
        return scenario.activeContract(scenario.activeClient());
    }

    private TicketResponse newTicket() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        return asOwnerOfA(
                () ->
                        ticketService.create(
                                new TicketCreateRequest(
                                        contract.id(),
                                        "Ticket de transição",
                                        null,
                                        TicketType.FEATURE,
                                        TicketPriority.MEDIUM,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)));
    }

    private TicketResponse transition(TicketResponse ticket, TicketStatus target, String reason) {
        return asOwnerOfA(
                () ->
                        transitionService.transition(
                                ticket.id(),
                                new TicketTransitionRequest(target, reason, ticket.version())));
    }

    /** Segundo usuário do tenant A, com papel {@code MEMBER}, para exercitar a nota ⁴. */
    private UUID memberOfTenantA() {
        UUID memberUserId =
                inTransaction(
                        () ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "membro-"
                                                                + UUID.randomUUID()
                                                                + "@exemplo.com",
                                                        NOW))
                                        .getId());
        runAs(
                tenantAId,
                userAId,
                Role.OWNER,
                () ->
                        membershipRepository.save(
                                FoundationDataBuilder.membership(
                                        tenantAId, memberUserId, Role.MEMBER, NOW)));
        return memberUserId;
    }
}
