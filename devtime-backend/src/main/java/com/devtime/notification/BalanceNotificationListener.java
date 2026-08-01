package com.devtime.notification;

import com.devtime.contract.BalanceService;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.event.BalanceEvents.AdjustmentAppliedEvent;
import com.devtime.contract.event.BalanceEvents.ConsumptionChangedEvent;
import com.devtime.contract.event.BalanceEvents.PeriodClosedEvent;
import com.devtime.contract.event.BalanceEvents.PeriodReopenedEvent;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.shared.tenancy.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notificações de saldo e de período (RN-602, RN-604, RN-241, RN-242, RN-215).
 *
 * <p><b>Todos os consumidores reagem após o commit da origem</b> (CP-16, TX-06). É obrigatório: a
 * notificação envolve entrega externa, e consumir dentro da transação faria uma falha de provedor
 * de e-mail reverter um fechamento de período — resultado inaceitável.
 *
 * <p>O contexto de tenant e o usuário ainda estão disponíveis: o ouvinte roda na mesma thread da
 * requisição, e {@code TenantContextFilter} só limpa depois da resposta. É o que permite resolver
 * destinatários pelo tenant correto sem carregá-lo no evento.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceNotificationListener {

    private static final String ENTITY_TYPE = "CONTRACT_PERIOD";

    private final NotificationService notificationService;
    private final RecipientResolver recipientResolver;
    private final ConsumptionAlertPolicy consumptionAlertPolicy;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;
    private final BalanceService balanceService;
    private final TenantContext tenantContext;

    /**
     * RN-602: os limiares são reavaliados a cada alteração de {@code consumedMinutes}.
     *
     * <p>Roda em rajada — vinte registros de horas num dia produzem vinte avaliações do mesmo
     * limiar. É a deduplicação que torna isso barato: dezenove das vinte inserções são rejeitadas
     * pelo índice único, em microssegundos (§20.1).
     *
     * <p>NT-05: quem registrou as horas não é notificado do próprio lançamento.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsumptionChanged(ConsumptionChangedEvent event) {
        evaluateThresholds(event.periodId());
    }

    /** RN-602: um ajuste altera o disponível e pode cruzar um limiar (FA-16 de notifications). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdjustmentApplied(AdjustmentAppliedEvent event) {
        UUID actor = currentUserId();
        Set<UUID> recipients = recipientResolver.forContractEvents(actor);
        if (!recipients.isEmpty()) {
            PeriodBalanceResponse balance = balanceService.getBalance(event.periodId());
            var text = renderer.adjustmentApplied(balance.label(), event.minutes());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("periodLabel", balance.label());
            payload.put("minutes", event.minutes());
            payload.put("availableMinutes", balance.availableMinutes());

            notificationService.notify(
                    new NotificationCommand(
                            recipients,
                            NotificationType.ADJUSTMENT_APPLIED,
                            NotificationType.ADJUSTMENT_APPLIED.getDefaultSeverity(),
                            text.title(),
                            text.body(),
                            renderer.payload(payload),
                            ENTITY_TYPE,
                            event.periodId(),
                            // §6.1: a chave inclui o destinatário — o mesmo ajuste é um fato novo
                            // para cada pessoa que responde pelo tenant.
                            recipient ->
                                    dedupeKeyBuilder.adjustmentApplied(
                                            event.adjustmentId(), recipient)));
        }
        evaluateThresholds(event.periodId());
    }

    /** RN-241 passo 7. CX-08: um período refechado não gera nova notificação de fechamento. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPeriodClosed(PeriodClosedEvent event) {
        Set<UUID> recipients = recipientResolver.forContractEvents(currentUserId());
        if (recipients.isEmpty()) {
            return;
        }
        PeriodBalanceResponse balance = balanceService.getBalance(event.periodId());
        var text =
                renderer.periodClosed(
                        balance.label(), balance.consumedMinutes(), event.carriedOutMinutes());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("periodLabel", balance.label());
        payload.put("consumedMinutes", balance.consumedMinutes());
        payload.put("carriedOutMinutes", event.carriedOutMinutes());

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.PERIOD_CLOSED,
                        NotificationType.PERIOD_CLOSED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(payload),
                        ENTITY_TYPE,
                        event.periodId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.periodClosed(event.periodId()))));
    }

    /**
     * RN-242: a reabertura altera um relatório <b>já entregue</b>.
     *
     * <p>CE-N-13: a chave inclui {@code reopenCount} — cada reabertura é um fato novo, ao contrário
     * do fechamento, que é sempre o mesmo fato.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPeriodReopened(PeriodReopenedEvent event) {
        Set<UUID> recipients = recipientResolver.forContractEvents(currentUserId());
        if (recipients.isEmpty()) {
            return;
        }
        PeriodBalanceResponse balance = balanceService.getBalance(event.periodId());
        var text = renderer.periodReopened(balance.label(), event.reopenCount());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("periodLabel", balance.label());
        payload.put("reopenCount", event.reopenCount());

        notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.PERIOD_REOPENED,
                        NotificationType.PERIOD_REOPENED.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(payload),
                        ENTITY_TYPE,
                        event.periodId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.periodReopened(
                                        event.periodId(), event.reopenCount()))));
    }

    private void evaluateThresholds(UUID periodId) {
        Set<UUID> recipients = recipientResolver.forContractEvents(currentUserId());
        consumptionAlertPolicy.evaluate(periodId, recipients).forEach(notificationService::notify);
    }

    /** NT-05: o autor da ação nunca é destinatário dela. */
    private UUID currentUserId() {
        return tenantContext.currentUserId().orElse(null);
    }
}
