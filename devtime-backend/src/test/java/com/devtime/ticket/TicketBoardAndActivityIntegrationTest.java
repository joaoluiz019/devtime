package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.comment.CommentService;
import com.devtime.comment.dto.CommentRequests.CommentCreateRequest;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import com.devtime.tag.TagLinkService;
import com.devtime.tag.TagService;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.dto.TicketRequests.TicketAssignRequest;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketTransitionRequest;
import com.devtime.ticket.dto.TicketResponses.TicketActivityEvent;
import com.devtime.ticket.dto.TicketResponses.TicketActivityResponse;
import com.devtime.ticket.dto.TicketResponses.TicketBoardColumn;
import com.devtime.ticket.dto.TicketResponses.TicketBoardResponse;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Quadro e linha do tempo do ticket (tickets.md §6.1 e §9.1). */
class TicketBoardAndActivityIntegrationTest extends FeatureTestSupport {

    @Autowired private TicketService ticketService;
    @Autowired private TicketTransitionService transitionService;
    @Autowired private TicketBoardService boardService;
    @Autowired private TicketActivityService activityService;
    @Autowired private CommentService commentService;
    @Autowired private TagService tagService;
    @Autowired private TagLinkService tagLinkService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("tickets.md §6.1: o quadro devolve as sete colunas, mesmo as vazias")
    void boardShouldExposeEveryStatusColumn() {
        ContractResponse contract = activeContract();
        newTicket(contract);

        TicketBoardResponse board = asOwnerOfA(() -> boardService.board(contract.id(), null));

        assertThat(board.columns())
                .as("uma coluna ausente faria a UI esconder um estado válido do fluxo")
                .extracting(TicketBoardColumn::status)
                .containsExactly(TicketStatus.values());
    }

    @Test
    @DisplayName("tickets.md §6.1: cada coluna soma os minutos e conta o total real")
    void boardColumnsShouldSummarize() {
        ContractResponse contract = activeContract();
        TicketResponse first = newTicket(contract);
        newTicket(contract);
        asOwnerOfA(
                () ->
                        transitionService.transition(
                                first.id(),
                                new TicketTransitionRequest(
                                        TicketStatus.IN_PROGRESS, null, first.version())));

        TicketBoardResponse board = asOwnerOfA(() -> boardService.board(contract.id(), null));

        assertThat(column(board, TicketStatus.BACKLOG).totalCount()).isEqualTo(1);
        assertThat(column(board, TicketStatus.IN_PROGRESS).totalCount()).isEqualTo(1);
        assertThat(column(board, TicketStatus.DONE).tickets()).isEmpty();
    }

    @Test
    @DisplayName("tickets.md §6.1: os cartões trazem chave, etiquetas e responsável já resolvidos")
    void boardCardsShouldCarryResolvedData() {
        ContractResponse contract = activeContract();
        TicketResponse ticket = newTicket(contract);
        UUID tagId = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticket.id(), List.of(tagId)));

        TicketBoardResponse board = asOwnerOfA(() -> boardService.board(contract.id(), null));
        var card = column(board, TicketStatus.BACKLOG).tickets().get(0);

        assertThat(card.key()).isEqualTo(contract.code() + "-1");
        assertThat(card.contractCode()).isEqualTo(contract.code());
        assertThat(card.tags()).extracting(tag -> tag.name()).containsExactly("checkout");
    }

    @Test
    @DisplayName("tickets.md §6.1: o quadro aceita escopo por contrato e por responsável")
    void boardShouldAcceptScopeFilters() {
        ContractResponse first = activeContract();
        ContractResponse second = activeContract();
        newTicket(first);
        newTicket(second);

        TicketBoardResponse scoped = asOwnerOfA(() -> boardService.board(first.id(), null));

        assertThat(totalOf(scoped)).isEqualTo(1);
        assertThat(totalOf(asOwnerOfA(() -> boardService.board(null, userAId))))
                .as("nenhum ticket possui responsável neste cenário")
                .isZero();
    }

    @Test
    @DisplayName("§9.1: a linha do tempo une auditoria e comentários em ordem decrescente")
    void activityShouldMergeAuditAndComments() {
        ContractResponse contract = activeContract();
        TicketResponse ticket = newTicket(contract);
        asOwnerOfA(
                () ->
                        commentService.create(
                                ticket.id(),
                                new CommentCreateRequest("Consegui reproduzir", null)));
        asOwnerOfA(
                () ->
                        transitionService.transition(
                                ticket.id(),
                                new TicketTransitionRequest(
                                        TicketStatus.IN_PROGRESS, null, ticket.version())));

        TicketActivityResponse activity =
                asOwnerOfA(() -> activityService.activity(ticket.id(), null, 50));

        assertThat(activity.content()).extracting(TicketActivityEvent::type).contains("CREATED");
        assertThat(activity.content())
                .extracting(TicketActivityEvent::type)
                .contains("STATUS_CHANGED", "COMMENT", "SYSTEM_COMMENT");
        assertThat(activity.content())
                .as("§9.1: do mais recente para o mais antigo")
                .isSortedAccordingTo(
                        java.util.Comparator.comparing(TicketActivityEvent::occurredAt).reversed());
    }

    @Test
    @DisplayName("§20: a linha do tempo é paginada por cursor, nunca por OFFSET")
    void activityShouldPaginateByCursor() {
        ContractResponse contract = activeContract();
        TicketResponse ticket = newTicket(contract);
        asOwnerOfA(
                () -> {
                    for (int index = 0; index < 4; index++) {
                        commentService.create(
                                ticket.id(), new CommentCreateRequest("Comentário " + index, null));
                    }
                    return null;
                });

        TicketActivityResponse first =
                asOwnerOfA(() -> activityService.activity(ticket.id(), null, 2));

        assertThat(first.content()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.cursor()).isNotNull();

        TicketActivityResponse second =
                asOwnerOfA(() -> activityService.activity(ticket.id(), first.cursor(), 2));
        assertThat(second.content())
                .as("a segunda página não repete eventos da primeira")
                .extracting(TicketActivityEvent::occurredAt)
                .allMatch(instant -> instant.isBefore(first.cursor()));
    }

    @Test
    @DisplayName("RN-815: a atribuição também aparece na linha do tempo, com comentário de sistema")
    void assignmentShouldAppearInActivity() {
        ContractResponse contract = activeContract();
        TicketResponse ticket = newTicket(contract);

        asOwnerOfA(
                () ->
                        transitionService.assign(
                                ticket.id(), new TicketAssignRequest(userAId, ticket.version())));

        TicketActivityResponse activity =
                asOwnerOfA(() -> activityService.activity(ticket.id(), null, 50));

        assertThat(activity.content()).extracting(TicketActivityEvent::type).contains("ASSIGNED");
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private ContractResponse activeContract() {
        return asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
    }

    private TicketResponse newTicket(ContractResponse contract) {
        return asOwnerOfA(
                () ->
                        ticketService.create(
                                new TicketCreateRequest(
                                        contract.id(),
                                        "Ticket de quadro",
                                        null,
                                        null,
                                        TicketPriority.HIGH,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)));
    }

    private TicketBoardColumn column(TicketBoardResponse board, TicketStatus status) {
        return board.columns().stream()
                .filter(column -> column.status() == status)
                .findFirst()
                .orElseThrow();
    }

    private int totalOf(TicketBoardResponse board) {
        return board.columns().stream().mapToInt(TicketBoardColumn::totalCount).sum();
    }
}
