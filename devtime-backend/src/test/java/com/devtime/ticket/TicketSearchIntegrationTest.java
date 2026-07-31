package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import com.devtime.tag.TagService;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketUpdateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import com.devtime.ticket.dto.TicketResponses.TicketSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Filtros compostos da listagem de tickets (tickets.md §6).
 *
 * <p>IMP-02: cada filtro precisa entrar na <b>consulta</b>. Um filtro aplicado em memória
 * devolveria a página certa com o total errado, e o cliente pagina sobre o total.
 */
class TicketSearchIntegrationTest extends FeatureTestSupport {

    @Autowired private TicketService ticketService;
    @Autowired private TicketTotalsService totalsService;
    @Autowired private TagService tagService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("tickets.md §6: filtro por situação, tipo e prioridade")
    void shouldFilterByStatusTypeAndPriority() {
        ContractResponse contract = activeContract();
        create(contract, "Bug urgente", TicketType.BUG, TicketPriority.URGENT);
        create(contract, "Melhoria média", TicketType.FEATURE, TicketPriority.MEDIUM);

        assertThat(
                        search(
                                contract.id(),
                                null,
                                List.of(TicketType.BUG),
                                null,
                                null,
                                null,
                                null,
                                null))
                .extracting(TicketSummaryResponse::title)
                .containsExactly("Bug urgente");
        assertThat(
                        search(
                                contract.id(),
                                null,
                                null,
                                List.of(TicketPriority.MEDIUM),
                                null,
                                null,
                                null,
                                null))
                .extracting(TicketSummaryResponse::title)
                .containsExactly("Melhoria média");
        assertThat(
                        search(
                                contract.id(),
                                List.of(TicketStatus.BACKLOG),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                .hasSize(2);
        assertThat(
                        search(
                                contract.id(),
                                List.of(TicketStatus.DONE),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                .isEmpty();
    }

    @Test
    @DisplayName("tickets.md §6: filtro por responsável e por relator")
    void shouldFilterByAssigneeAndReporter() {
        ContractResponse contract = activeContract();
        TicketResponse assigned =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Com responsável",
                                                null,
                                                null,
                                                null,
                                                userAId,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)));
        create(contract, "Sem responsável", null, null);

        assertThat(search(contract.id(), null, null, null, userAId, null, null, null))
                .extracting(TicketSummaryResponse::id)
                .containsExactly(assigned.id());
        assertThat(search(contract.id(), null, null, null, null, userAId, null, null))
                .as("o relator é sempre o usuário autenticado na criação")
                .hasSize(2);
    }

    @Test
    @DisplayName("tickets.md §6: a busca textual cobre título e descrição, sem diferenciar caixa")
    void searchShouldCoverTitleAndDescription() {
        ContractResponse contract = activeContract();
        asOwnerOfA(
                () ->
                        ticketService.create(
                                new TicketCreateRequest(
                                        contract.id(),
                                        "Ajuste no fluxo de pagamento",
                                        "O problema está no cálculo do FRETE",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)));
        create(contract, "Outro assunto qualquer", null, null);

        assertThat(search(contract.id(), null, null, null, null, null, "PAGAMENTO", null))
                .hasSize(1);
        assertThat(search(contract.id(), null, null, null, null, null, "frete", null))
                .as("a busca alcança a descrição, não só o título")
                .hasSize(1);
        assertThat(search(contract.id(), null, null, null, null, null, "inexistente", null))
                .isEmpty();
    }

    @Test
    @DisplayName("RN-309: o filtro isOverEstimate separa os estourados dos demais")
    void shouldFilterByOverEstimate() {
        ContractResponse contract = activeContract();
        TicketResponse estimated =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Com estimativa",
                                                null,
                                                null,
                                                null,
                                                null,
                                                60,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)));
        create(contract, "Sem estimativa", null, null);
        asOwnerOfA(
                () -> {
                    totalsService.applyWorkLogDelta(estimated.id(), 120, 120);
                    return null;
                });

        assertThat(search(contract.id(), null, null, null, null, null, null, true))
                .extracting(TicketSummaryResponse::id)
                .containsExactly(estimated.id());
        assertThat(search(contract.id(), null, null, null, null, null, null, false))
                .as(
                        "CX-09: sem estimativa não há estouro — o ticket entra no grupo dos não estourados")
                .hasSize(1);
    }

    @Test
    @DisplayName("tickets.md §6: o filtro por cliente resolve os contratos dele na consulta")
    void shouldFilterByClient() {
        UUID clientId = asOwnerOfA(() -> scenario.activeClient());
        ContractResponse ofClient =
                asOwnerOfA(() -> scenario.activeContract(clientId, "Do cliente"));
        ContractResponse ofOther = activeContract();
        create(ofClient, "Do cliente filtrado", null, null);
        create(ofOther, "De outro cliente", null, null);

        PageResponse<TicketSummaryResponse> page =
                asOwnerOfA(
                        () ->
                                ticketService.search(
                                        null,
                                        clientId,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PageRequest.of(0, 20)));

        assertThat(page.content())
                .extracting(TicketSummaryResponse::title)
                .containsExactly("Do cliente filtrado");
        assertThat(page.totalElements())
                .as("IMP-02: o total reflete a consulta filtrada, não a filtragem em memória")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("tickets.md §6: cliente sem contratos não devolve ticket algum")
    void clientWithoutContractsShouldReturnNothing() {
        UUID emptyClient = asOwnerOfA(() -> scenario.activeClient());
        create(activeContract(), "De outro cliente", null, null);

        PageResponse<TicketSummaryResponse> page =
                asOwnerOfA(
                        () ->
                                ticketService.search(
                                        null,
                                        emptyClient,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        PageRequest.of(0, 20)));

        assertThat(page.content()).isEmpty();
    }

    @Test
    @DisplayName("tickets.md §6: o filtro por etiquetas é conjuntivo e entra na consulta")
    void tagFilterShouldBeConjunctive() {
        ContractResponse contract = activeContract();
        UUID urgente = asOwnerOfA(() -> tagService.resolveOrCreate("urgente"));
        UUID checkout = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));
        TicketResponse both =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Com as duas etiquetas",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                List.of(urgente, checkout),
                                                null,
                                                null)));
        asOwnerOfA(
                () ->
                        ticketService.create(
                                new TicketCreateRequest(
                                        contract.id(),
                                        "Com uma etiqueta",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(urgente),
                                        null,
                                        null)));

        PageResponse<TicketSummaryResponse> page =
                asOwnerOfA(
                        () ->
                                ticketService.search(
                                        contract.id(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(urgente, checkout),
                                        null,
                                        null,
                                        PageRequest.of(0, 20)));

        assertThat(page.content()).extracting(TicketSummaryResponse::id).containsExactly(both.id());
    }

    @Test
    @DisplayName("RN-011: a atualização preserva number e chave, e troca as etiquetas")
    void updateShouldPreserveKeyAndReplaceTags() {
        ContractResponse contract = activeContract();
        UUID urgente = asOwnerOfA(() -> tagService.resolveOrCreate("urgente"));
        UUID checkout = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));
        TicketResponse ticket =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Título original",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                List.of(urgente),
                                                null,
                                                null)));

        TicketResponse updated =
                asOwnerOfA(
                        () ->
                                ticketService.update(
                                        ticket.id(),
                                        new TicketUpdateRequest(
                                                "Título revisado",
                                                "Descrição nova",
                                                TicketType.SUPPORT,
                                                TicketPriority.LOW,
                                                240,
                                                null,
                                                null,
                                                List.of(checkout),
                                                "GH-1234",
                                                ticket.version())));

        assertThat(updated.key()).isEqualTo(ticket.key());
        assertThat(updated.number()).isEqualTo(ticket.number());
        assertThat(updated.title()).isEqualTo("Título revisado");
        assertThat(updated.type()).isEqualTo(TicketType.SUPPORT);
        assertThat(updated.tags()).extracting(tag -> tag.name()).containsExactly("checkout");
        assertThat(updated.externalRef()).isEqualTo("GH-1234");
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private ContractResponse activeContract() {
        return asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
    }

    private void create(
            ContractResponse contract, String title, TicketType type, TicketPriority priority) {
        asOwnerOfA(
                () ->
                        ticketService.create(
                                new TicketCreateRequest(
                                        contract.id(),
                                        title,
                                        null,
                                        type,
                                        priority,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)));
    }

    private List<TicketSummaryResponse> search(
            UUID contractId,
            List<TicketStatus> statuses,
            List<TicketType> types,
            List<TicketPriority> priorities,
            UUID assigneeId,
            UUID reporterId,
            String search,
            Boolean overEstimate) {
        return asOwnerOfA(
                        () ->
                                ticketService.search(
                                        contractId,
                                        null,
                                        statuses,
                                        types,
                                        priorities,
                                        assigneeId,
                                        reporterId,
                                        null,
                                        search,
                                        overEstimate,
                                        PageRequest.of(0, 20)))
                .content();
    }
}
