package com.devtime.notification;

import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.report.event.ExportEvents.ExportCompletedEvent;
import com.devtime.report.event.ExportEvents.ExportFailedEvent;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Aviso de exportação concluída ou falha (§15 de {@code specs/012}, FA-10, CE-R-11).
 *
 * <p>Fecha a pendência registrada na nota ¹¹ de {@code implementation-order.md} §12: {@code
 * EXPORT_COMPLETED} e {@code EXPORT_FAILED} estavam no catálogo sem produtor, aguardando {@code
 * 012}.
 *
 * <p><b>NT-05 não se aplica.</b> Na maioria dos eventos o autor da ação é excluído dos
 * destinatários — ninguém precisa ser avisado do que acabou de fazer. Aqui o solicitante é
 * <b>exatamente</b> quem precisa saber: ele pediu um arquivo grande, recebeu {@code 202} e foi
 * fazer outra coisa. Sem o aviso, restaria a ele adivinhar quando voltar à tela.
 *
 * <p>§19.1 e CP-18: a notificação carrega formato e contagem, <b>nunca</b> os filtros nem conteúdo
 * de linha. Um alerta que dissesse "sua exportação do cliente Acme está pronta" espalharia o
 * recorte do relatório por e-mail.
 */
@Component
@RequiredArgsConstructor
public class ExportNotificationListener {

    private static final String ENTITY_TYPE = "REPORT_EXECUTION";

    private final NotificationService notificationService;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;

    /** BR-128 / TX-06: após o commit. Um e-mail que falhe não desfaz a exportação concluída. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExportCompleted(ExportCompletedEvent event) {
        var text = renderer.exportCompleted(event.format(), event.rowCount());
        notificationService.notify(
                new NotificationCommand(
                        Set.of(event.requestedBy()),
                        NotificationType.EXPORT_COMPLETED,
                        NotificationType.EXPORT_COMPLETED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(
                                Map.of("format", event.format(), "rowCount", event.rowCount())),
                        ENTITY_TYPE,
                        event.executionId(),
                        // Uma exportação conclui uma única vez: COMPLETED só sai de lá para
                        // EXPIRED, que não notifica.
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.forType(
                                        NotificationType.EXPORT_COMPLETED,
                                        event.executionId(),
                                        null))));
    }

    /**
     * CE-R-11: a falha vira aviso com o motivo, e nenhum outro fluxo é afetado.
     *
     * <p>A chave de deduplicação inclui a tentativa: duas falhas do mesmo pedido são dois fatos
     * distintos, e a segunda é a que informa ao usuário que não haverá terceira (CP-16).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExportFailed(ExportFailedEvent event) {
        var text = renderer.exportFailed(event.format(), event.failureReason());
        notificationService.notify(
                new NotificationCommand(
                        Set.of(event.requestedBy()),
                        NotificationType.EXPORT_FAILED,
                        NotificationType.EXPORT_FAILED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(
                                Map.of(
                                        "format",
                                        event.format(),
                                        "failureReason",
                                        event.failureReason())),
                        ENTITY_TYPE,
                        event.executionId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.forType(
                                        NotificationType.EXPORT_FAILED,
                                        event.executionId(),
                                        event.failureReason()))));
    }
}
