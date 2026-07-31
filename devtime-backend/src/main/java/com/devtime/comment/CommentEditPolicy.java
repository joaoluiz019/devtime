package com.devtime.comment;

import com.devtime.comment.domain.Comment;
import com.devtime.comment.domain.CommentExceptions;
import com.devtime.shared.error.OwnershipViolationException;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Janela de edição e moderação (RN-812, RN-815).
 *
 * <p><b>{@code ADMIN}/{@code OWNER} excluem, mas não editam.</b> Excluir é ato de moderação —
 * remover conteúdo inadequado. Editar o comentário de outra pessoa é falsificar o que ela disse. A
 * distinção é estrutural, não uma verificação: {@code COMMENT_UPDATE_ANY} <b>não existe</b> no
 * catálogo da §6.8 de {@code permissions.md}, então não há permissão que a conceda a ninguém.
 *
 * <p><b>A janela é estritamente menor que 24 horas</b> (CX-09): exatamente 24h já está fora. O
 * limite existe para corrigir erro recente, não para reescrever o histórico da conversa.
 */
@Component
@RequiredArgsConstructor
public class CommentEditPolicy {

    /** RN-812. */
    public static final Duration EDIT_WINDOW = Duration.ofHours(24);

    private final TenantContext tenantContext;
    private final TenantClock clock;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2707} para comentário
     *     de sistema, {@code DEVTIME-1103} para autor divergente, {@code DEVTIME-2706} fora da
     *     janela
     */
    public void assertEditable(Comment comment) {
        if (comment.isSystem()) {
            throw CommentExceptions.systemImmutable(); // RN-815, INV-CMT-03
        }
        UUID currentUserId = tenantContext.requireUserId();
        if (!currentUserId.equals(comment.getAuthorId())) {
            // §6.3: nem ADMIN edita comentário de terceiro (CX-12, FA-10).
            throw new OwnershipViolationException("Comment"); // OWN-03
        }
        Duration elapsed = Duration.between(comment.getCreatedAt(), clock.now());
        if (elapsed.compareTo(EDIT_WINDOW) >= 0) {
            throw CommentExceptions.editWindowExpired(elapsed.toHours()); // RN-812
        }
    }

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2707} para comentário
     *     de sistema, {@code DEVTIME-1103} quando não é autor nem moderador
     */
    public void assertDeletable(Comment comment) {
        if (comment.isSystem()) {
            throw CommentExceptions.systemImmutable(); // RN-815
        }
        if (canModerate()) {
            // RN-812: moderação a qualquer momento, inclusive fora da janela (FA-09).
            return;
        }
        UUID currentUserId = tenantContext.requireUserId();
        if (!currentUserId.equals(comment.getAuthorId())) {
            throw new OwnershipViolationException("Comment"); // OWN-03
        }
        Duration elapsed = Duration.between(comment.getCreatedAt(), clock.now());
        if (elapsed.compareTo(EDIT_WINDOW) >= 0) {
            throw CommentExceptions.editWindowExpired(elapsed.toHours()); // RN-812
        }
    }

    /**
     * §23 da spec: {@code canEdit} e {@code canDelete} são calculados no <b>servidor</b>.
     *
     * <p>O cliente não deve reimplementar a janela de 24h e o ownership — duas implementações
     * divergiriam, e a do cliente seria a que o usuário vê.
     */
    public boolean canEdit(Comment comment) {
        if (comment.isSystem() || comment.getAuthorId() == null) {
            return false;
        }
        return tenantContext.currentUserId().map(comment.getAuthorId()::equals).orElse(false)
                && Duration.between(comment.getCreatedAt(), clock.now()).compareTo(EDIT_WINDOW) < 0;
    }

    public boolean canDelete(Comment comment) {
        if (comment.isSystem()) {
            return false;
        }
        return canModerate() || canEdit(comment);
    }

    private boolean canModerate() {
        return tenantContext.currentPermissions().contains(Permission.COMMENT_DELETE_ANY);
    }
}
