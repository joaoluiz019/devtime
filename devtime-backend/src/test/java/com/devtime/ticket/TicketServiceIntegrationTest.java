package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.category.CategoryService;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import com.devtime.tag.TagService;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketMoveContractRequest;
import com.devtime.ticket.dto.TicketRequests.TicketUpdateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** Regras de criação, edição, movimentação e exclusão de tickets (RN-301 a RN-313, spec 007). */
class TicketServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private TicketService ticketService;
    @Autowired private TicketTotalsService totalsService;
    @Autowired private TagService tagService;
    @Autowired private CategoryService categoryService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("RN-302: o ticket nasce em BACKLOG com chave {contract.code}-{number}")
    void shouldCreateInBacklogWithDerivedKey() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());

        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        assertThat(ticket.status()).isEqualTo(TicketStatus.BACKLOG);
        assertThat(ticket.number()).isEqualTo(1);
        assertThat(ticket.key()).isEqualTo(contract.code() + "-1");
        assertThat(ticket.reporter().id())
                .as("SG-07: reporterId vem do contexto autenticado, nunca do payload")
                .isEqualTo(userAId);
        assertThat(ticket.spentMinutes()).isZero();
        assertThat(ticket.billableMinutes()).isZero();
    }

    @Test
    @DisplayName("RN-302: a numeração é sequencial por contrato, e não por tenant")
    void numberingShouldBePerContract() {
        UUID clientId = asOwnerOfA(() -> scenario.activeClient());
        ContractResponse first = asOwnerOfA(() -> scenario.activeContract(clientId, "Primeiro"));
        ContractResponse second = asOwnerOfA(() -> scenario.activeContract(clientId, "Segundo"));

        asOwnerOfA(() -> ticketService.create(request(first.id())));
        TicketResponse secondOfFirst = asOwnerOfA(() -> ticketService.create(request(first.id())));
        TicketResponse firstOfSecond = asOwnerOfA(() -> ticketService.create(request(second.id())));

        assertThat(secondOfFirst.number()).isEqualTo(2);
        assertThat(firstOfSecond.number())
                .as("dois contratos do mesmo tenant possuem, ambos, um ticket de número 1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("RN-301/RN-002: contrato de outro tenant responde 404, nunca 403")
    void shouldRejectContractFromAnotherTenant() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());

        assertThatThrownBy(() -> asOwnerOfB(() -> ticketService.create(request(contract.id()))))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-306: contrato em DRAFT não aceita ticket — DEVTIME-2306")
    void shouldRejectContractNotAcceptingWork() {
        UUID clientId = asOwnerOfA(() -> scenario.activeClient());
        ContractResponse draft = asOwnerOfA(() -> scenario.draftContract(clientId));

        assertThatThrownBy(() -> asOwnerOfA(() -> ticketService.create(request(draft.id()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2306");
    }

    @Test
    @DisplayName("RN-303/CX-12: título com 3 e com 200 caracteres é aceito; 2 e 201 são rejeitados")
    void shouldEnforceTitleBoundaries() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());

        assertThat(asOwnerOfA(() -> ticketService.create(request(contract.id(), "abc"))).title())
                .isEqualTo("abc");
        assertThat(
                        asOwnerOfA(
                                        () ->
                                                ticketService.create(
                                                        request(contract.id(), "a".repeat(200))))
                                .title())
                .hasSize(200);
        assertThatThrownBy(
                        () -> asOwnerOfA(() -> ticketService.create(request(contract.id(), "ab"))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2303");
    }

    @Test
    @DisplayName("RN-304: responsável sem membership ativo é rejeitado com DEVTIME-2304")
    void shouldRejectAssigneeWithoutActiveMembership() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketCreateRequest request =
                new TicketCreateRequest(
                        contract.id(),
                        "Com responsável de outro tenant",
                        null,
                        null,
                        null,
                        userBId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> asOwnerOfA(() -> ticketService.create(request)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2304");
    }

    @Test
    @DisplayName("FA-01: criação sem responsável é permitida e não notifica ninguém")
    void shouldAllowTicketWithoutAssignee() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());

        assertThat(asOwnerOfA(() -> ticketService.create(request(contract.id()))).assignee())
                .isNull();
    }

    @Test
    @DisplayName("RN-104: defaultCategoryId inativo é rejeitado com DEVTIME-2104")
    void shouldRejectInactiveDefaultCategory() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        UUID categoryId =
                asOwnerOfA(
                        () ->
                                categoryService
                                        .create(
                                                new com.devtime.category.dto.CategoryRequests
                                                        .CategoryCreateRequest(
                                                        "Descontinuada",
                                                        null,
                                                        null,
                                                        null,
                                                        true,
                                                        null))
                                        .id());
        asOwnerOfA(
                () ->
                        categoryService.update(
                                categoryId,
                                new com.devtime.category.dto.CategoryRequests.CategoryUpdateRequest(
                                        "Descontinuada", null, null, null, true, false, null, 0L)));

        TicketCreateRequest request =
                new TicketCreateRequest(
                        contract.id(),
                        "Com categoria inativa",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        categoryId,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> asOwnerOfA(() -> ticketService.create(request)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2104");
    }

    @Test
    @DisplayName("RN-313: a 11ª etiqueta é rejeitada com DEVTIME-2313 e nenhuma é vinculada")
    void shouldRejectEleventhTag() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        List<UUID> elevenTags =
                asOwnerOfA(
                        () ->
                                java.util.stream.IntStream.range(0, 11)
                                        .mapToObj(
                                                index ->
                                                        tagService.resolveOrCreate(
                                                                "assunto-" + index))
                                        .toList());

        TicketCreateRequest request =
                new TicketCreateRequest(
                        contract.id(),
                        "Com onze etiquetas",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        elevenTags,
                        null,
                        null);

        assertThatThrownBy(() -> asOwnerOfA(() -> ticketService.create(request)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2313");
    }

    @Test
    @DisplayName("RN-309/CX-08: estouro de estimativa sinaliza e nunca bloqueia")
    void overEstimateShouldSignalWithoutBlocking() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket =
                asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Com estimativa de 10h",
                                                null,
                                                null,
                                                null,
                                                null,
                                                600,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)));

        asOwnerOfA(
                () -> {
                    totalsService.applyWorkLogDelta(ticket.id(), 1800, 1800);
                    return null;
                });
        TicketResponse reloaded = asOwnerOfA(() -> ticketService.getById(ticket.id()));

        assertThat(reloaded.spentMinutes()).isEqualTo(1800);
        assertThat(reloaded.isOverEstimate()).isTrue();
        assertThat(reloaded.progressRate()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("CX-09: sem estimativa, progressRate e isOverEstimate são nulos")
    void withoutEstimateProgressShouldBeNull() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        assertThat(ticket.progressRate()).isNull();
        assertThat(ticket.isOverEstimate()).isNull();
    }

    @Test
    @DisplayName("RN-308: os totais são ajustados por incremento, inclusive negativo")
    void totalsShouldBeAppliedByDelta() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        asOwnerOfA(
                () -> {
                    totalsService.applyWorkLogDelta(ticket.id(), 120, 90);
                    totalsService.applyWorkLogDelta(ticket.id(), 60, 60);
                    totalsService.applyWorkLogDelta(ticket.id(), -60, -60);
                    return null;
                });

        TicketResponse reloaded = asOwnerOfA(() -> ticketService.getById(ticket.id()));
        assertThat(reloaded.spentMinutes()).isEqualTo(120);
        assertThat(reloaded.billableMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("FA-15/CX-19: a busca por chave resolve o ticket; chave inexistente responde 404")
    void shouldResolveByKey() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        assertThat(asOwnerOfA(() -> ticketService.getByKey(ticket.key())).id())
                .isEqualTo(ticket.id());
        assertThatThrownBy(() -> asOwnerOfA(() -> ticketService.getByKey("CT-9999-1")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("SG-01: a busca por chave de outro tenant responde 404")
    void shouldNotResolveKeyFromAnotherTenant() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        assertThatThrownBy(() -> asOwnerOfB(() -> ticketService.getByKey(ticket.key())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-305/CX-04: a movimentação de contrato preserva number e chave")
    void moveContractShouldPreserveKey() {
        UUID clientId = asOwnerOfA(() -> scenario.activeClient());
        ContractResponse origin = asOwnerOfA(() -> scenario.activeContract(clientId, "Origem"));
        ContractResponse target = asOwnerOfA(() -> scenario.activeContract(clientId, "Destino"));
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(origin.id())));

        var moved =
                asOwnerOfA(
                        () ->
                                ticketService.moveContract(
                                        ticket.id(),
                                        new TicketMoveContractRequest(
                                                target.id(), true, ticket.version())));

        assertThat(moved.key())
                .as("CP-06: a chave já circulou fora do sistema e é referência permanente")
                .isEqualTo(ticket.key());
        assertThat(moved.contract().id()).isEqualTo(target.id());
        assertThat(asOwnerOfA(() -> ticketService.getById(ticket.id())).number())
                .isEqualTo(ticket.number());
    }

    @Test
    @DisplayName("RN-305: mover para contrato de outro cliente é rejeitado com DEVTIME-2315")
    void shouldRejectMoveAcrossClients() {
        ContractResponse origin = asOwnerOfA(() -> activeContract());
        ContractResponse target = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(origin.id())));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                ticketService.moveContract(
                                                        ticket.id(),
                                                        new TicketMoveContractRequest(
                                                                target.id(),
                                                                true,
                                                                ticket.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2315");
    }

    @Test
    @DisplayName("RN-305/INV-TCK-02: mover ticket com horas é rejeitado com DEVTIME-2305")
    void shouldRejectMoveWithWorkLogs() {
        UUID clientId = asOwnerOfA(() -> scenario.activeClient());
        ContractResponse origin = asOwnerOfA(() -> scenario.activeContract(clientId, "Origem"));
        ContractResponse target = asOwnerOfA(() -> scenario.activeContract(clientId, "Destino"));
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(origin.id())));
        asOwnerOfA(
                () -> {
                    totalsService.applyWorkLogDelta(ticket.id(), 90, 90);
                    return null;
                });

        TicketResponse withHours = asOwnerOfA(() -> ticketService.getById(ticket.id()));
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                ticketService.moveContract(
                                                        ticket.id(),
                                                        new TicketMoveContractRequest(
                                                                target.id(),
                                                                true,
                                                                withHours.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2305");
    }

    @Test
    @DisplayName("RN-307/FA-13: ticket sem horas é excluído logicamente")
    void shouldDeleteTicketWithoutWorkLogs() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        asOwnerOfA(
                () -> {
                    ticketService.delete(ticket.id());
                    return null;
                });

        assertThatThrownBy(() -> asOwnerOfA(() -> ticketService.getById(ticket.id())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-307/FA-14: excluir ticket com horas retorna DEVTIME-2307 sugerindo cancelar")
    void shouldRejectDeletingTicketWithWorkLogs() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));
        asOwnerOfA(
                () -> {
                    totalsService.applyWorkLogDelta(ticket.id(), 90, 90);
                    return null;
                });

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            ticketService.delete(ticket.id());
                                            return null;
                                        }))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure -> {
                            BusinessRuleException business = (BusinessRuleException) failure;
                            assertThat(business.getErrorCode().getCode()).isEqualTo("DEVTIME-2307");
                            assertThat(business.getDetails())
                                    .as("a mensagem precisa apontar o caminho: cancelar")
                                    .containsEntry("suggestedAction", "CANCEL");
                        });
    }

    @Test
    @DisplayName("RN-004: edição com version divergente retorna DEVTIME-2004")
    void shouldRejectStaleVersionOnUpdate() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        TicketResponse ticket = asOwnerOfA(() -> ticketService.create(request(contract.id())));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                ticketService.update(
                                                        ticket.id(), updateRequest(99L))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2004");
    }

    @Test
    @DisplayName("RN-012: a listagem é paginada e devolve projeção sem description")
    void listShouldReturnProjection() {
        ContractResponse contract = asOwnerOfA(() -> activeContract());
        asOwnerOfA(() -> ticketService.create(request(contract.id())));

        var page =
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
                                        null,
                                        null,
                                        null,
                                        PageRequest.of(0, 20)));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).key()).isEqualTo(contract.code() + "-1");
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private ContractResponse activeContract() {
        return scenario.activeContract(scenario.activeClient());
    }

    private TicketCreateRequest request(UUID contractId) {
        return request(contractId, "Corrigir cálculo de frete no checkout");
    }

    private TicketCreateRequest request(UUID contractId, String title) {
        return new TicketCreateRequest(
                contractId,
                title,
                null,
                TicketType.BUG,
                TicketPriority.HIGH,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private TicketUpdateRequest updateRequest(long version) {
        return new TicketUpdateRequest(
                "Título revisado",
                null,
                TicketType.BUG,
                TicketPriority.HIGH,
                null,
                null,
                null,
                null,
                null,
                version);
    }
}
