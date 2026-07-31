package com.devtime.comment;

import com.devtime.audit.AuditService;
import com.devtime.comment.domain.Comment;
import com.devtime.comment.domain.SystemCommentTrigger;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação de comentários de sistema (ver {@link SystemCommentService}).
 *
 * <p>Sem {@code @PreAuthorize}: o ator é o sistema e a operação ignora RBAC (CE-P-08). Exigir
 * permissão aqui impediria, por exemplo, que um {@code MEMBER} transicionasse um ticket próprio — a
 * transição dele geraria um comentário que ele não teria permissão para criar. Não existe rota HTTP
 * que alcance este serviço, e é essa ausência que o protege.
 *
 * <p>{@code Propagation.REQUIRED} (o padrão): participa da transação da transição. Se ela for
 * revertida, o comentário também é — caso contrário a linha do tempo afirmaria fatos que não
 * ocorreram.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SystemCommentServiceImpl implements SystemCommentService {

    private final CommentRepository repository;
    private final AuditService auditService;

    @Override
    public UUID emit(UUID ticketId, SystemCommentTrigger trigger, String body) {
        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        // INV-CMT-03: sem autor — não há pessoa a quem atribuir um registro automático.
        comment.setAuthorId(null);
        comment.setBody(body);
        // RN-815: comentário de sistema nunca é resposta e nunca menciona ninguém.
        comment.setParentCommentId(null);
        comment.setMentionedUserIds(new UUID[0]);
        comment.setSystem(true);
        comment.setSystemTrigger(trigger);

        Comment saved = repository.save(comment);

        auditService.recordSystemAction(
                "COMMENT_SYSTEM_CREATED",
                "Comment",
                saved.getId(),
                Map.of("ticketId", ticketId, "trigger", trigger.name()));

        // §28: DEBUG e sem o texto gerado.
        log.debug(
                "comentário de sistema criado commentId={} ticketId={} trigger={}",
                saved.getId(),
                ticketId,
                trigger);
        return saved.getId();
    }
}
