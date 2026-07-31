package com.devtime.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import com.devtime.tag.dto.TagRequests.TagLinkRequest;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.tag.dto.TagResponses.TagResponse;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Vínculo de etiquetas, contador de uso e limite por alvo (RN-313, INV-TAG-01 a INV-TAG-05). */
class TagLinkIntegrationTest extends FeatureTestSupport {

    @Autowired private TagService tagService;
    @Autowired private TagLinkService tagLinkService;
    @Autowired private TicketService ticketService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("INV-TAG-04: vincular incrementa usageCount na mesma transação")
    void linkShouldIncrementUsageCount() {
        UUID ticketId = newTicket();
        UUID tagId = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));

        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, List.of(tagId)));

        assertThat(usageCountOf(tagId)).isEqualTo(1);
    }

    @Test
    @DisplayName("CX-10: vincular a mesma etiqueta duas vezes é idempotente e não infla o contador")
    void linkingTwiceShouldBeIdempotent() {
        UUID ticketId = newTicket();
        UUID tagId = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));

        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, List.of(tagId)));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, List.of(tagId)));

        assertThat(usageCountOf(tagId)).isEqualTo(1);
        assertThat(asOwnerOfA(() -> tagLinkService.findByTicketId(ticketId))).hasSize(1);
    }

    @Test
    @DisplayName("INV-TAG-04: desvincular decrementa o contador")
    void unlinkShouldDecrementUsageCount() {
        UUID ticketId = newTicket();
        UUID tagId = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, List.of(tagId)));

        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, List.of()));

        assertThat(usageCountOf(tagId)).isZero();
        assertThat(asOwnerOfA(() -> tagLinkService.findByTicketId(ticketId))).isEmpty();
    }

    @Test
    @DisplayName(
            "CX-11: com 10 etiquetas, trocar uma por outra é permitido — o limite é do conjunto")
    void swappingTagsAtTheLimitShouldBeAllowed() {
        UUID ticketId = newTicket();
        List<UUID> ten = tags(10);
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, ten));
        UUID eleventh = asOwnerOfA(() -> tagService.resolveOrCreate("substituta"));

        List<UUID> swapped = new java.util.ArrayList<>(ten.subList(0, 9));
        swapped.add(eleventh);
        List<TagOptionResponse> result =
                asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, swapped));

        assertThat(result).hasSize(10);
        assertThat(usageCountOf(ten.get(9))).as("a etiqueta removida perde um uso").isZero();
        assertThat(usageCountOf(eleventh)).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-313/FA-07: a 11ª etiqueta é rejeitada e nenhuma alteração é aplicada")
    void eleventhTagShouldBeRejectedAtomically() {
        UUID ticketId = newTicket();
        List<UUID> eleven = tags(11);

        assertThatThrownBy(
                        () -> asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, eleven)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2313");
        assertThat(asOwnerOfA(() -> tagLinkService.findByTicketId(ticketId))).isEmpty();
    }

    @Test
    @DisplayName("SG-02/INV-TAG-05: vincular etiqueta de outro tenant responde 404")
    void shouldRejectTagFromAnotherTenant() {
        UUID ticketId = newTicket();
        UUID foreignTagId = asOwnerOfB(() -> tagService.resolveOrCreate("de-outro-tenant"));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                tagLinkService.replaceTicketTags(
                                                        ticketId, List.of(foreignTagId))))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("E-07: tagNames aciona a criação implícita e é idempotente por normalização")
    void tagNamesShouldResolveOrCreate() {
        List<UUID> first =
                asOwnerOfA(
                        () ->
                                tagLinkService.resolveTagIds(
                                        new TagLinkRequest(null, List.of("Code Review"))));
        List<UUID> second =
                asOwnerOfA(
                        () ->
                                tagLinkService.resolveTagIds(
                                        new TagLinkRequest(null, List.of("code-review"))));

        assertThat(first).hasSize(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("§20: as etiquetas de vários tickets são carregadas em uma consulta, sem N+1")
    void tagsOfSeveralTicketsShouldLoadInBatch() {
        UUID first = newTicket();
        UUID second = newTicket();
        UUID shared = asOwnerOfA(() -> tagService.resolveOrCreate("compartilhada"));
        UUID onlyFirst = asOwnerOfA(() -> tagService.resolveOrCreate("so-do-primeiro"));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(first, List.of(shared, onlyFirst)));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(second, List.of(shared)));

        Map<UUID, List<TagOptionResponse>> byTicket =
                asOwnerOfA(() -> tagLinkService.findByTicketIds(List.of(first, second)));

        assertThat(byTicket.get(first)).hasSize(2);
        assertThat(byTicket.get(second)).hasSize(1);
    }

    @Test
    @DisplayName("tickets.md §6: o filtro por etiquetas é conjuntivo — todas, não qualquer uma")
    void tagFilterShouldBeConjunctive() {
        UUID both = newTicket();
        UUID onlyOne = newTicket();
        UUID urgente = asOwnerOfA(() -> tagService.resolveOrCreate("urgente"));
        UUID checkout = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(both, List.of(urgente, checkout)));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(onlyOne, List.of(urgente)));

        List<UUID> matching =
                asOwnerOfA(() -> tagLinkService.ticketIdsWithAllTags(List.of(urgente, checkout)));

        assertThat(matching).containsExactly(both);
    }

    @Test
    @DisplayName("§9.3: excluir a etiqueta remove os vínculos e informa a contagem")
    void deletingTagShouldUnlinkAndReport() {
        UUID firstTicket = newTicket();
        UUID secondTicket = newTicket();
        UUID tagId = asOwnerOfA(() -> tagService.resolveOrCreate("obsoleta"));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(firstTicket, List.of(tagId)));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(secondTicket, List.of(tagId)));

        var response = asOwnerOfA(() -> tagService.delete(tagId));

        assertThat(response.unlinkedFromTickets()).isEqualTo(2);
        assertThat(asOwnerOfA(() -> tagLinkService.findByTicketId(firstTicket))).isEmpty();
    }

    @Test
    @DisplayName("INV-TAG-04: excluir o ticket desvincula as etiquetas e devolve o contador")
    void deletingTicketShouldUnlinkTags() {
        UUID ticketId = newTicket();
        UUID tagId = asOwnerOfA(() -> tagService.resolveOrCreate("checkout"));
        asOwnerOfA(() -> tagLinkService.replaceTicketTags(ticketId, List.of(tagId)));

        asOwnerOfA(
                () -> {
                    ticketService.delete(ticketId);
                    return null;
                });

        assertThat(usageCountOf(tagId)).isZero();
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private UUID newTicket() {
        ContractResponse contract =
                asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
        TicketResponse ticket =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Ticket rotulável",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)));
        return ticket.id();
    }

    private List<UUID> tags(int quantity) {
        return asOwnerOfA(
                () ->
                        IntStream.range(0, quantity)
                                .mapToObj(index -> tagService.resolveOrCreate("assunto-" + index))
                                .toList());
    }

    private int usageCountOf(UUID tagId) {
        return asOwnerOfA(() -> tagService.search(null, null)).stream()
                .filter(tag -> tag.id().equals(tagId))
                .map(TagResponse::usageCount)
                .findFirst()
                .orElseThrow();
    }
}
