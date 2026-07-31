package com.devtime.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.comment.dto.CommentRequests.CommentCreateRequest;
import com.devtime.comment.dto.CommentRequests.CommentUpdateRequest;
import com.devtime.comment.dto.CommentResponses.CommentResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.support.TicketScenario;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.TicketTransitionService;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketRequests.TicketTransitionRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Regras da conversa do ticket (RN-811 a RN-815, spec 014). */
class CommentServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private CommentService commentService;
    @Autowired private TicketService ticketService;
    @Autowired private TicketTransitionService transitionService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName("RN-811/CX-01: corpo com 1 e com 10.000 caracteres é aceito")
    void shouldAcceptBodyBoundaries() {
        UUID ticketId = newTicket();

        assertThat(create(ticketId, "a").body()).isEqualTo("a");
        assertThat(create(ticketId, "a".repeat(10_000)).body()).hasSize(10_000);
    }

    @Test
    @DisplayName("RN-811/CX-02: corpo só com espaços é rejeitado com DEVTIME-2705")
    void shouldRejectBlankBody() {
        UUID ticketId = newTicket();

        assertThatThrownBy(() -> create(ticketId, "   "))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2705");
    }

    @Test
    @DisplayName("RN-811: corpo com 10.001 caracteres é rejeitado com DEVTIME-2705")
    void shouldRejectOversizedBody() {
        UUID ticketId = newTicket();

        assertThatThrownBy(() -> create(ticketId, "a".repeat(10_001)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2705");
    }

    @Test
    @DisplayName("RN-814/CX-03: responder a uma resposta vincula à raiz, não à resposta")
    void replyToReplyShouldAttachToRoot() {
        UUID ticketId = newTicket();
        CommentResponse root = create(ticketId, "Consegui reproduzir");
        CommentResponse reply = reply(ticketId, "Aqui também", root.id());

        CommentResponse replyToReply = reply(ticketId, "E aqui", reply.id());

        assertThat(reply.parentCommentId()).isEqualTo(root.id());
        assertThat(replyToReply.parentCommentId())
                .as("a árvore permanece plana por construção — dois níveis garantidos")
                .isEqualTo(root.id());
    }

    @Test
    @DisplayName("INV-CMT-02/CX-04: responder a comentário de outro ticket é rejeitado")
    void shouldRejectParentFromAnotherTicket() {
        UUID first = newTicket();
        UUID second = newTicket();
        CommentResponse rootOfFirst = create(first, "Comentário do primeiro ticket");

        assertThatThrownBy(() -> reply(second, "Resposta cruzada", rootOfFirst.id()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-813: menção a membro ativo é resolvida; menção inexistente fica como texto")
    void shouldResolveOnlyActiveMembers() {
        UUID ticketId = newTicket();
        UUID memberId = activeMemberWithHandle("ana");

        CommentResponse comment = create(ticketId, "@ana revisar isso, @carlos também");

        assertThat(comment.mentionedUsers())
                .extracting(summary -> summary.id())
                .containsExactly(memberId);
    }

    @Test
    @DisplayName("RN-813/CX-05: menção repetida aparece uma única vez")
    void repeatedMentionShouldAppearOnce() {
        UUID ticketId = newTicket();
        activeMemberWithHandle("ana");

        CommentResponse comment = create(ticketId, "@ana @ana confere");

        assertThat(comment.mentionedUsers()).hasSize(1);
    }

    @Test
    @DisplayName("CX-16: padrão de e-mail não é tratado como menção")
    void emailPatternShouldNotBeMention() {
        UUID ticketId = newTicket();
        activeMemberWithHandle("ana");

        CommentResponse comment = create(ticketId, "escreva para ana@dominio.com");

        assertThat(comment.mentionedUsers()).isEmpty();
    }

    @Test
    @DisplayName("RN-812: o autor edita dentro da janela e editedAt é preenchido")
    void authorShouldEditWithinWindow() {
        UUID ticketId = newTicket();
        CommentResponse comment = create(ticketId, "Versão original");

        CommentResponse edited =
                asOwnerOfA(
                        () ->
                                commentService.update(
                                        comment.id(),
                                        new CommentUpdateRequest(
                                                "Versão corrigida", comment.version())));

        assertThat(edited.body()).isEqualTo("Versão corrigida");
        assertThat(edited.editedAt()).isNotNull();
    }

    @Test
    @DisplayName("RN-812/§6.3/CX-12: nem OWNER edita comentário de terceiro — DEVTIME-1103")
    void ownerShouldNotEditOthersComment() {
        UUID ticketId = newTicket();
        UUID memberId = activeMemberWithHandle("bruno");
        CommentResponse fromMember =
                runAs(
                        tenantAId,
                        memberId,
                        Role.MEMBER,
                        () ->
                                commentService.create(
                                        ticketId,
                                        new CommentCreateRequest("Comentário do membro", null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                commentService.update(
                                                        fromMember.id(),
                                                        new CommentUpdateRequest(
                                                                "Reescrito pelo dono",
                                                                fromMember.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-1103");
    }

    @Test
    @DisplayName("RN-812/FA-09: OWNER exclui comentário de terceiro — moderação é permitida")
    void ownerShouldModerateOthersComment() {
        UUID ticketId = newTicket();
        UUID memberId = activeMemberWithHandle("bruno");
        CommentResponse fromMember =
                runAs(
                        tenantAId,
                        memberId,
                        Role.MEMBER,
                        () ->
                                commentService.create(
                                        ticketId,
                                        new CommentCreateRequest("Conteúdo a moderar", null)));

        asOwnerOfA(
                () -> {
                    commentService.delete(fromMember.id());
                    return null;
                });

        assertThat(asOwnerOfA(() -> commentService.listByTicket(ticketId, null, 20)).content())
                .extracting(CommentResponse::id)
                .doesNotContain(fromMember.id());
    }

    @Test
    @DisplayName("§23: canEdit e canDelete são calculados no servidor, por autor e janela")
    void serverShouldComputePermissionFlags() {
        UUID ticketId = newTicket();
        UUID memberId = activeMemberWithHandle("bruno");
        CommentResponse fromMember =
                runAs(
                        tenantAId,
                        memberId,
                        Role.MEMBER,
                        () ->
                                commentService.create(
                                        ticketId,
                                        new CommentCreateRequest("Comentário do membro", null)));

        CommentResponse seenByOwner =
                asOwnerOfA(() -> commentService.listByTicket(ticketId, null, 20)).content().stream()
                        .filter(comment -> comment.id().equals(fromMember.id()))
                        .findFirst()
                        .orElseThrow();

        assertThat(seenByOwner.canEdit())
                .as("editar o que outra pessoa disse é falsificação, não moderação")
                .isFalse();
        assertThat(seenByOwner.canDelete()).isTrue();
    }

    @Test
    @DisplayName("RN-815: a transição gera comentário de sistema imutável, dentro da transação")
    void transitionShouldEmitImmutableSystemComment() {
        UUID ticketId = newTicket();
        TicketResponse ticket = asOwnerOfA(() -> ticketService.getById(ticketId));

        asOwnerOfA(
                () ->
                        transitionService.transition(
                                ticketId,
                                new TicketTransitionRequest(
                                        TicketStatus.IN_PROGRESS, null, ticket.version())));

        CommentResponse systemComment =
                asOwnerOfA(() -> commentService.listByTicket(ticketId, null, 20)).content().stream()
                        .filter(CommentResponse::isSystem)
                        .findFirst()
                        .orElseThrow();

        assertThat(systemComment.author())
                .as("não há pessoa a quem atribuir um registro automático")
                .isNull();
        assertThat(systemComment.canEdit()).isFalse();
        assertThat(systemComment.canDelete()).isFalse();
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                commentService.update(
                                                        systemComment.id(),
                                                        new CommentUpdateRequest(
                                                                "Reescrito",
                                                                systemComment.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2707");
    }

    @Test
    @DisplayName("CX-08: excluir uma raiz preserva as respostas de terceiros")
    void deletingRootShouldPreserveReplies() {
        UUID ticketId = newTicket();
        CommentResponse root = create(ticketId, "Raiz da conversa");
        CommentResponse reply = reply(ticketId, "Resposta que deve sobreviver", root.id());

        asOwnerOfA(
                () -> {
                    commentService.delete(root.id());
                    return null;
                });

        assertThat(asOwnerOfA(() -> commentService.existsForComment(reply.id()))).isTrue();
    }

    @Test
    @DisplayName("RN-002/SG-01: comentário de outro tenant responde 404, nunca 403")
    void shouldNotReachCommentFromAnotherTenant() {
        UUID ticketId = newTicket();
        CommentResponse comment = create(ticketId, "Conteúdo do tenant A");

        assertThatThrownBy(
                        () ->
                                asOwnerOfB(
                                        () -> {
                                            commentService.delete(comment.id());
                                            return null;
                                        }))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("CX-14: Markdown com payload de XSS é armazenado como texto, sem interpretação")
    void shouldStoreScriptPayloadAsPlainText() {
        UUID ticketId = newTicket();

        CommentResponse comment = create(ticketId, "<script>alert('xss')</script>");

        assertThat(comment.body()).isEqualTo("<script>alert('xss')</script>");
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private UUID newTicket() {
        ContractResponse contract =
                asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));
        return asOwnerOfA(
                        () ->
                                ticketService.create(
                                        new TicketCreateRequest(
                                                contract.id(),
                                                "Ticket com conversa",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))
                .id();
    }

    private CommentResponse create(UUID ticketId, String body) {
        return asOwnerOfA(
                () -> commentService.create(ticketId, new CommentCreateRequest(body, null)));
    }

    private CommentResponse reply(UUID ticketId, String body, UUID parentId) {
        return asOwnerOfA(
                () -> commentService.create(ticketId, new CommentCreateRequest(body, parentId)));
    }

    /** Membro ativo do tenant A com identificador de exibição, para exercitar RN-813. */
    private UUID activeMemberWithHandle(String handle) {
        UUID memberUserId =
                inTransaction(
                        () -> {
                            var user =
                                    FoundationDataBuilder.user(
                                            handle + "-" + UUID.randomUUID() + "@exemplo.com", NOW);
                            user.setDisplayName(handle);
                            return userRepository.save(user).getId();
                        });
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
