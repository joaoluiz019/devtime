package com.devtime.comment;

import com.devtime.comment.domain.SystemCommentTrigger;
import java.util.UUID;

/**
 * Comentários de sistema (RN-815).
 *
 * <p>São criados <b>dentro</b> da transação da transição que os originou: um status alterado sem o
 * comentário correspondente deixa a linha do tempo incompleta, e ambos são o mesmo fato (§15 da
 * spec).
 *
 * <p>Fecha a dívida OB-06 de {@code specs/007-tickets}: até esta feature existir, o emissor daquela
 * gravava apenas o {@code AuditLog}.
 */
public interface SystemCommentService {

    /**
     * Cria o comentário automático.
     *
     * @param body texto factual do fato ocorrido, produzido por {@link SystemCommentTemplates}
     * @return o identificador do comentário criado
     */
    UUID emit(UUID ticketId, SystemCommentTrigger trigger, String body);
}
