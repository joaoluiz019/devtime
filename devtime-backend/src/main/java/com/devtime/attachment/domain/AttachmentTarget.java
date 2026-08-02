package com.devtime.attachment.domain;

import java.util.UUID;

/**
 * Alvo do anexo (INV-ATT-01, E-01).
 *
 * <p>Modelado como tipo próprio, e não como dois parâmetros nuláveis, porque a exclusividade é uma
 * invariante e não uma validação: com dois parâmetros, toda assinatura de método permitiria
 * expressar o estado proibido, e a proibição precisaria ser reverificada em cada uma delas.
 *
 * @param kind ticket ou comentário
 * @param id identificador do alvo, no tenant corrente
 */
public record AttachmentTarget(Kind kind, UUID id) {

    public AttachmentTarget {
        if (kind == null || id == null) {
            throw new IllegalArgumentException("AttachmentTarget exige kind e id");
        }
    }

    public enum Kind {
        /** RN-806: até 20 anexos. */
        TICKET(20),
        /** RN-806: até 5 anexos. */
        COMMENT(5);

        private final int maxAttachments;

        Kind(int maxAttachments) {
            this.maxAttachments = maxAttachments;
        }

        /** CX-19: os limites são por alvo e independentes entre si. */
        public int maxAttachments() {
            return maxAttachments;
        }
    }

    public static AttachmentTarget ticket(UUID ticketId) {
        return new AttachmentTarget(Kind.TICKET, ticketId);
    }

    public static AttachmentTarget comment(UUID commentId) {
        return new AttachmentTarget(Kind.COMMENT, commentId);
    }

    public boolean isTicket() {
        return kind == Kind.TICKET;
    }

    public UUID ticketId() {
        return isTicket() ? id : null;
    }

    public UUID commentId() {
        return isTicket() ? null : id;
    }
}
