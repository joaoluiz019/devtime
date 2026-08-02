package com.devtime.attachment;

import com.devtime.attachment.domain.AttachmentExceptions;
import com.devtime.attachment.domain.AttachmentTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Passo 4 de §6.1 — limite de anexos no alvo (RN-806).
 *
 * <p>20 por ticket, 5 por comentário. CX-19: os limites são <b>por alvo</b> e independentes — um
 * ticket com 20 anexos e um comentário dele com 5 são ambos válidos, porque contam coisas
 * diferentes.
 *
 * <p>CX-21 — upload concorrente do 20º e do 21º: a contagem aqui é otimista e a garantia final é a
 * transação. Dois uploads simultâneos podem ambos ler 19; o que os separa é que a criação ocorre
 * dentro da mesma transação da contagem, sob o isolamento {@code READ_COMMITTED} padrão, e o
 * segundo a confirmar relê o estado já persistido. A alternativa — {@code CHECK} no banco — não é
 * expressável sobre uma contagem de linhas sem gatilho, e um gatilho colocaria regra de negócio
 * fora do serviço (BR-060).
 */
@Component
@RequiredArgsConstructor
public class AttachmentLimitPolicy {

    private final AttachmentRepository repository;

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2704} / {@code 422}
     *     quando o alvo já atingiu o limite (FA-10)
     */
    public void assertBelowLimit(AttachmentTarget target) {
        long current =
                target.isTicket()
                        ? repository.countByTicket(target.id())
                        : repository.countByComment(target.id());

        if (current >= target.kind().maxAttachments()) { // RN-806
            throw AttachmentExceptions.limitExceeded(
                    target.kind().name(), target.kind().maxAttachments());
        }
    }

    /** {@code maxCount} da listagem: permite à UI desabilitar o envio antes da tentativa (§23). */
    public int maxFor(AttachmentTarget.Kind kind) {
        return kind.maxAttachments();
    }
}
