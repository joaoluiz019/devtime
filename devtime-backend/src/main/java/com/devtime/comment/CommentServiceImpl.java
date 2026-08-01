package com.devtime.comment;

import com.devtime.audit.AuditService;
import com.devtime.comment.domain.Comment;
import com.devtime.comment.domain.CommentExceptions;
import com.devtime.comment.dto.CommentRequests.CommentCreateRequest;
import com.devtime.comment.dto.CommentRequests.CommentUpdateRequest;
import com.devtime.comment.dto.CommentResponses.CommentResponse;
import com.devtime.comment.dto.CommentResponses.CommentThreadResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.ticket.TicketService;
import com.devtime.user.UserService;
import com.devtime.user.dto.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras da conversa do ticket (spec 014 §6).
 *
 * <p>A ordem de {@link #create} segue exatamente a §6.1 da spec e é normativa (BR-062).
 *
 * <p>§28 da spec: <b>{@code body} nunca entra em log</b>. É o campo mais longo e mais provável de
 * conter dado pessoal de terceiros. A auditoria — que é dado do tenant, não log de aplicação —
 * preserva o corpo anterior nas edições, e é o único lugar onde o texto original sobrevive, já que
 * não existe histórico de versões.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CommentServiceImpl implements CommentService {

    private static final String ENTITY_TYPE = "Comment";

    /** §20 da spec: máximo de raízes por página. */
    private static final int MAX_PAGE_SIZE = 50;

    private final CommentRepository repository;
    private final CommentMapper mapper;
    private final CommentThreadPolicy threadPolicy;
    private final CommentEditPolicy editPolicy;
    private final MentionExtractor mentionExtractor;
    private final TicketService ticketService;
    private final com.devtime.shared.event.DomainEventPublisher events;
    private final UserService userService;
    private final AuditService auditService;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'COMMENT_VIEW')")
    public CommentThreadResponse listByTicket(UUID ticketId, Instant cursor, int size) {
        requireTicket(ticketId);
        int pageSize = Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE); // RN-012

        // pageSize + 1 revela se existe próxima página sem uma segunda consulta de contagem.
        List<Comment> roots =
                repository.findRootsByTicket(ticketId, cursor, PageRequest.of(0, pageSize + 1));
        boolean hasMore = roots.size() > pageSize;
        List<Comment> page = hasMore ? roots.subList(0, pageSize) : roots;

        // Respostas de todas as raízes em uma consulta: uma por raiz seria N+1 (§25 da spec).
        List<Comment> replies =
                page.isEmpty()
                        ? List.of()
                        : repository.findRepliesByParents(
                                page.stream().map(Comment::getId).toList());

        Map<UUID, UserSummary> people = resolvePeople(page, replies);
        Map<UUID, List<Comment>> repliesByRoot =
                replies.stream().collect(Collectors.groupingBy(Comment::getParentCommentId));

        List<CommentResponse> content =
                page.stream()
                        .map(
                                root ->
                                        toResponse(
                                                root,
                                                people,
                                                repliesByRoot
                                                        .getOrDefault(root.getId(), List.of())
                                                        .stream()
                                                        .map(
                                                                reply ->
                                                                        toResponse(
                                                                                reply, people,
                                                                                List.of()))
                                                        .toList()))
                        .toList();

        Instant nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getCreatedAt();
        return new CommentThreadResponse(
                content, nextCursor, hasMore, repository.countByTicket(ticketId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'COMMENT_CREATE')")
    public CommentResponse create(UUID ticketId, CommentCreateRequest request) {
        requireTicket(ticketId); // passo 2 — 404 para ticket de outro tenant
        String body = requireBody(request.body()); // passo 3 — RN-811
        // passos 4 e 5 — INV-CMT-02 e RN-814: resposta a resposta vai para a raiz.
        UUID rootId = threadPolicy.resolveRoot(request.parentCommentId(), ticketId);

        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(tenantContext.requireUserId()); // passo 7 — nunca da requisição
        comment.setBody(body);
        comment.setParentCommentId(rootId);
        // passo 6 — RN-813: menções não resolvidas permanecem como texto, sem erro.
        comment.setMentionedUserIds(mentionExtractor.extract(body).toArray(UUID[]::new));
        comment.setSystem(false);

        Comment saved = repository.save(comment);

        // passo 9 — RN-006, na mesma transação.
        auditService.record(
                "COMMENT_CREATED",
                ENTITY_TYPE,
                saved.getId(),
                Map.of(),
                Map.of(
                        "ticketId",
                        ticketId,
                        "isSystem",
                        false,
                        "mentionCount",
                        saved.getMentionedUserIds().length));

        // §15 de specs/013: publicado após a persistência e consumido APÓS O COMMIT — uma falha
        // de e-mail não pode reverter um comentário já escrito (CP-16, TX-06).
        var ticket = ticketService.getRef(ticketId);
        events.publish(
                new com.devtime.comment.event.CommentEvents.CommentCreatedEvent(
                        saved.getId(),
                        ticketId,
                        ticket.key(),
                        saved.getAuthorId(),
                        assigneeOf(ticketId),
                        List.of(saved.getMentionedUserIds())));

        // §28: nem o corpo, nem trecho dele.
        log.info(
                "comentário criado commentId={} ticketId={} mentions={}",
                saved.getId(),
                ticketId,
                saved.getMentionedUserIds().length);
        return toResponse(saved, resolvePeople(List.of(saved), List.of()), List.of());
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'COMMENT_UPDATE_OWN')")
    public CommentResponse update(UUID commentId, CommentUpdateRequest request) {
        Comment comment = require(commentId);
        assertVersion(comment, request.version()); // RN-004
        editPolicy.assertEditable(comment); // RN-812, RN-815

        String previousBody = comment.getBody();
        String body = requireBody(request.body()); // RN-811
        comment.setBody(body);
        comment.setEditedAt(clock.now());
        // CX-10: menções reextraídas; as novas notificam, as anteriores não são renotificadas.
        comment.setMentionedUserIds(mentionExtractor.extract(body).toArray(UUID[]::new));

        // §18 da spec: a edição registra o corpo anterior completo. É o único lugar onde o texto
        // original sobrevive — não existe histórico de versões.
        auditService.record(
                "COMMENT_UPDATED",
                ENTITY_TYPE,
                commentId,
                Map.of("body", previousBody),
                Map.of("body", body));

        log.info(
                "comentário editado commentId={} horasDesdeCriacao={}",
                commentId,
                java.time.Duration.between(comment.getCreatedAt(), clock.now()).toHours());
        return toResponse(comment, resolvePeople(List.of(comment), List.of()), List.of());
    }

    /**
     * Exclusão lógica (RN-003).
     *
     * <p>CX-08: excluir uma raiz que possui respostas <b>preserva as respostas</b>. Excluí-las em
     * cascata destruiria a contribuição de terceiros por decisão de uma pessoa só; a interface
     * ocupa o lugar do original com um marcador de conteúdo removido.
     */
    @Override
    @Transactional
    @PreAuthorize(
            "hasPermission(null, 'COMMENT_UPDATE_OWN') or hasPermission(null, 'COMMENT_DELETE_ANY')")
    public void delete(UUID commentId) {
        Comment comment = require(commentId);
        editPolicy.assertDeletable(comment); // RN-812, RN-815

        boolean own = tenantContext.requireUserId().equals(comment.getAuthorId());
        repository.softDelete(commentId, clock.now(), tenantContext.currentUserId().orElse(null));

        auditService.record(
                "COMMENT_DELETED",
                ENTITY_TYPE,
                commentId,
                Map.of("authorId", comment.getAuthorId()),
                Map.of("deletedAt", clock.now().toString()),
                Map.of("byModerator", !own));
        log.info("comentário excluído commentId={} proprio={}", commentId, own);
    }

    @Override
    public boolean existsForComment(UUID commentId) {
        return commentId != null && repository.existsById(commentId);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private Comment require(UUID id) {
        // ART-024: inexistente e de outro tenant produzem a mesma resposta.
        return repository
                .findById(id)
                .orElseThrow(() -> EntityNotFoundException.of(Comment.class, id));
    }

    /**
     * O ticket é validado pela interface pública de {@code 007}, nunca pelo repositório (AR-02).
     */
    private void requireTicket(UUID ticketId) {
        ticketService.getById(ticketId);
    }

    /**
     * RN-607: responsável do ticket, destinatário de {@code TICKET_COMMENTED}.
     *
     * <p>Resolvido aqui e enviado no evento para que {@code 013} não precise reconsultar o ticket —
     * BR-181 permite identificadores, e é o identificador que o destinatário exige.
     */
    private UUID assigneeOf(UUID ticketId) {
        var assignee = ticketService.getById(ticketId).assignee();
        return assignee == null ? null : assignee.id();
    }

    private String requireBody(String rawBody) {
        String body = rawBody == null ? "" : rawBody.strip();
        // CX-02: corpo só com espaços é rejeitado — a validação ocorre após aparar.
        if (body.length() < 1 || body.length() > 10_000) {
            throw CommentExceptions.bodyInvalid(body.length()); // RN-811
        }
        return body;
    }

    private void assertVersion(Comment comment, long expected) {
        if (comment.getVersion() != null && comment.getVersion() != expected) {
            throw BusinessRuleException.versionConflict(ENTITY_TYPE, expected); // RN-004
        }
    }

    /** Autores e mencionados de toda a página em <b>uma</b> consulta (§20 da spec). */
    private Map<UUID, UserSummary> resolvePeople(List<Comment> roots, List<Comment> replies) {
        Set<UUID> ids = new LinkedHashSet<>();
        java.util.stream.Stream.concat(roots.stream(), replies.stream())
                .forEach(
                        comment -> {
                            if (comment.getAuthorId() != null) {
                                ids.add(comment.getAuthorId());
                            }
                            ids.addAll(Arrays.asList(comment.getMentionedUserIds()));
                        });
        Map<UUID, UserSummary> resolved = new HashMap<>(userService.findSummaries(ids));
        // RN-458: autor removido do tenant continua vinculado; apenas o nome exibido muda.
        ids.forEach(id -> resolved.computeIfAbsent(id, UserSummary::removed));
        return resolved;
    }

    private CommentResponse toResponse(
            Comment comment, Map<UUID, UserSummary> people, List<CommentResponse> replies) {
        List<UserSummary> mentioned = new ArrayList<>();
        for (UUID mentionedId : comment.getMentionedUserIds()) {
            mentioned.add(people.getOrDefault(mentionedId, UserSummary.removed(mentionedId)));
        }
        return mapper.toResponse(
                comment,
                comment.getAuthorId() == null ? null : people.get(comment.getAuthorId()),
                mentioned,
                editPolicy.canEdit(comment),
                editPolicy.canDelete(comment),
                replies);
    }
}
