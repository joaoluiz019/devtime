package com.devtime.notification;

import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.ticket.TicketService;
import com.devtime.timer.event.TimerEvents.TimerAbandonedEvent;
import com.devtime.timer.event.TimerEvents.TimerForceStoppedEvent;
import com.devtime.timer.event.TimerEvents.TimerLongRunningEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notificações de cronômetro (RN-163, RN-164, OWN-05).
 *
 * <p><b>RN-607 / FA-19: o destinatário é sempre o dono do cronômetro, nunca um gestor.</b> O
 * cronômetro representa trabalho em andamento de uma pessoa específica, e OWN-05 é a regra de
 * propriedade mais restritiva do sistema — a notificação a acompanha.
 *
 * <p>FA-20 / CX-21: no encerramento forçado, quem recebe é o <b>dono</b>, e não o administrador que
 * encerrou. Interferir no cronômetro de alguém sem que essa pessoa saiba produziria um registro de
 * horas que ela não reconhece como seu.
 */
@Component
@RequiredArgsConstructor
public class TimerNotificationListener {

    /** RN-165: a janela de recuperação exibida no corpo da notificação de abandono. */
    private static final int RECOVERY_WINDOW_DAYS = 7;

    private static final String ENTITY_TYPE = "TIMER";

    private final NotificationService notificationService;
    private final RecipientResolver recipientResolver;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;
    private final TicketService ticketService;

    /**
     * RN-163: emitida <b>uma única vez</b> por cronômetro.
     *
     * <p>CX-05: o job marca {@code longRunningNotifiedAt} antes de publicar, e o {@code dedupeKey}
     * é a segunda barreira — uma nova execução do job não duplica o alerta.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimerLongRunning(TimerLongRunningEvent event) {
        Set<UUID> recipients = recipientResolver.forTimerEvent(event.userId(), null);
        if (recipients.isEmpty()) {
            return;
        }
        String ticketKey = ticketKeyOf(event.ticketId());
        var text = renderer.timerLongRunning(ticketKey, event.grossElapsedSeconds());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketKey", ticketKey);
        payload.put("grossElapsedSeconds", event.grossElapsedSeconds());

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.TIMER_LONG_RUNNING,
                        NotificationType.TIMER_LONG_RUNNING.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(payload),
                        ENTITY_TYPE,
                        event.timerId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.timerLongRunning(event.timerId()))));
    }

    /**
     * RN-164: o texto deixa explícito que <b>nenhum registro foi criado</b> e que o tempo é
     * recuperável.
     *
     * <p>É a informação que importa: RN-164 existe para não inventar um horário de término, e o
     * usuário precisa saber que o trabalho não foi perdido — apenas ainda não foi registrado.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimerAbandoned(TimerAbandonedEvent event) {
        Set<UUID> recipients = recipientResolver.forTimerEvent(event.userId(), null);
        if (recipients.isEmpty()) {
            return;
        }
        String ticketKey = ticketKeyOf(event.ticketId());
        // RN-165: o prazo vem do evento — quem conhece a janela de 7 dias é 009
        // (AbandonedTimerPolicy).
        var text = renderer.timerAbandoned(ticketKey, event.recoverableUntil());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketKey", ticketKey);
        payload.put("grossElapsedSeconds", event.grossElapsedSeconds());
        payload.put("recoveryWindowDays", RECOVERY_WINDOW_DAYS);

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.TIMER_ABANDONED,
                        NotificationType.TIMER_ABANDONED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(payload),
                        ENTITY_TYPE,
                        event.timerId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.timerAbandoned(event.timerId()))));
    }

    /** SG-07 / OWN-05: o dono é notificado; o administrador que encerrou, não (NT-05). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimerForceStopped(TimerForceStoppedEvent event) {
        Set<UUID> recipients = recipientResolver.forTimerEvent(event.ownerId(), event.stoppedBy());
        if (recipients.isEmpty()) {
            return;
        }
        String ticketKey = ticketKeyOf(event.ticketId());
        var text = renderer.timerForceStopped(ticketKey);

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.TIMER_FORCE_STOPPED,
                        NotificationType.TIMER_FORCE_STOPPED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(Map.of("ticketKey", ticketKey)),
                        ENTITY_TYPE,
                        event.timerId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.timerForceStopped(event.timerId()))));
    }

    /**
     * O evento carrega apenas identificadores (BR-181); a chave legível é resolvida aqui.
     *
     * <p>Quando o ticket não é alcançável — cronômetro de outro tenant no momento da leitura, por
     * exemplo —, o corpo usa um marcador em vez de falhar: uma notificação sem a chave do ticket
     * ainda é melhor que nenhuma notificação.
     */
    private String ticketKeyOf(UUID ticketId) {
        try {
            return ticketService.getKeyById(ticketId);
        } catch (RuntimeException unavailable) {
            return "—";
        }
    }
}
