package com.devtime.notification;

import com.devtime.attachment.event.AttachmentEvents.AttachmentInfectedEvent;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notificação de anexo infectado (RN-803, §15 de {@code specs/015}).
 *
 * <p>Fecha a pendência registrada na nota ¹¹ de {@code implementation-order.md} §12: {@code
 * ATTACHMENT_INFECTED} estava declarado no catálogo sem produtor, aguardando {@code 015}.
 *
 * <p><b>NT-05 não se aplica aqui.</b> Em todos os outros eventos o autor da ação é excluído dos
 * destinatários — ninguém precisa ser avisado do que acabou de fazer. Neste, quem enviou é
 * <b>exatamente</b> quem precisa saber: ele não sabe que o arquivo estava infectado, e sem o aviso
 * veria apenas um download que não funciona (CP-20). O ator da transição, aliás, é o sistema, não
 * quem enviou.
 *
 * <p>{@code CRITICAL} e {@code canMute = false} (§9.1): é incidente de segurança, e um usuário não
 * pode silenciar o aviso de que introduziu um arquivo malicioso.
 */
@Component
@RequiredArgsConstructor
public class AttachmentNotificationListener {

    private static final String ENTITY_TYPE = "ATTACHMENT";

    private final NotificationService notificationService;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;

    /**
     * CP-16 / TX-06: após o commit.
     *
     * <p>A transição para {@code INFECTED} e a remoção do binário precisam ser atômicas entre si
     * (INV-ATT-06); o aviso, não. Uma falha de e-mail não pode reverter a remoção de um arquivo
     * malicioso — seria trocar um incidente resolvido por um incidente ativo.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentInfected(AttachmentInfectedEvent event) {
        var text = renderer.attachmentInfected(event.threat());
        notificationService.notify(
                new NotificationCommand(
                        Set.of(event.uploadedBy()),
                        NotificationType.ATTACHMENT_INFECTED,
                        NotificationType.ATTACHMENT_INFECTED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        // §19.1: nem o nome do arquivo, nem o IP do upload. O IP pertence à
                        // trilha de auditoria, que é para investigação, não para exibição.
                        renderer.payload(Map.of("threat", event.threat())),
                        ENTITY_TYPE,
                        event.attachmentId(),
                        // Um anexo é infectado uma única vez: INFECTED é terminal (§11.1).
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.forType(
                                        NotificationType.ATTACHMENT_INFECTED,
                                        event.attachmentId(),
                                        null))));
    }
}
