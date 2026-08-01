package com.devtime.notification;

import com.devtime.contract.BalanceService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.BalanceResponses.PeriodBalanceResponse;
import com.devtime.contract.dto.ContractResponses.ContractRefResponse;
import com.devtime.notification.domain.NotificationSeverity;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Avaliação dos limiares de consumo (RN-602, RN-604, §6.3).
 *
 * <p><b>Os limiares vêm do contrato, nunca de 50/80/100 fixos</b> (CP-05). Um contrato configurado
 * com {@code [70, 90]} alerta em 70 e 90 — e usar valores fixos faria a notificação divergir do
 * painel do mesmo contrato, que lê a mesma configuração.
 *
 * <p><b>Nada aqui decide se já foi notificado.</b> A política monta um comando por limiar
 * ultrapassado, sempre; quem deduplica é o índice único, pelo {@code dedupeKey} (RN-601). É o que
 * torna a avaliação idempotente por construção: rodar vinte vezes no mesmo dia produz vinte
 * comandos e uma notificação.
 *
 * <p>CX-04 / CE-10: contrato {@code HOURLY_OPEN} tem {@code availableMinutes = 0} por definição
 * (RN-210) e <b>nenhum</b> alerta de consumo é avaliado — um modelo de horas abertas não tem teto a
 * ultrapassar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsumptionAlertPolicy {

    /** §6.1: acima de 100% a severidade é crítica, qualquer que seja o limiar configurado. */
    private static final int CRITICAL_THRESHOLD = 100;

    /** §6.1: entre 80% e 100%, aviso. */
    private static final int WARNING_THRESHOLD = 80;

    private static final String ENTITY_TYPE = "CONTRACT_PERIOD";

    private final BalanceService balanceService;
    private final ContractService contractService;
    private final DedupeKeyBuilder dedupeKeyBuilder;
    private final NotificationTemplateRenderer renderer;

    /**
     * Comandos a criar para o período, na ordem em que os limiares foram configurados.
     *
     * <p>FA-04 / CX-03: um salto de 0% a 105% produz os três limiares <b>e</b> o excedente em uma
     * única avaliação — cada um é um fato distinto e cada um tem a sua chave.
     *
     * @param recipients destinatários já resolvidos (RN-607); vazio produz lista vazia
     */
    public List<NotificationCommand> evaluate(UUID periodId, Set<UUID> recipients) {
        if (recipients.isEmpty()) {
            return List.of(); // FA-05
        }
        PeriodBalanceResponse balance = balanceService.getBalance(periodId);

        // CE-10 / CX-04: sem saldo contratado não há limiar a cruzar. A verificação é sobre o
        // disponível, e não sobre o tipo do contrato, porque é o disponível que define o teto —
        // e AR-02 impede esta feature de conhecer ContractType.
        if (balance.availableMinutes() <= 0) {
            return List.of();
        }

        ContractRefResponse contract = contractService.getRefById(balance.contractId());
        List<Integer> thresholds = contractService.notificationThresholdsOf(balance.contractId());
        BigDecimal rate = balance.consumptionRate();

        List<NotificationCommand> commands = new java.util.ArrayList<>();
        for (int threshold : thresholds) {
            if (rate.compareTo(BigDecimal.valueOf(threshold)) < 0) {
                continue;
            }
            commands.add(consumptionCommand(balance, contract, threshold, recipients));
        }

        // RN-604: o excedente é notificação própria, além do limiar de 100%. São fatos diferentes —
        // "atingiu o teto" e "passou do teto" — e o segundo tem severidade crítica sempre.
        if (balance.overageMinutes() > 0) {
            commands.add(overageCommand(balance, contract, recipients));
        }
        return List.copyOf(commands);
    }

    private NotificationCommand consumptionCommand(
            PeriodBalanceResponse balance,
            ContractRefResponse contract,
            int threshold,
            Set<UUID> recipients) {
        var text =
                renderer.consumption(
                        contract.name(),
                        balance.label(),
                        threshold,
                        balance.consumedMinutes(),
                        balance.availableMinutes(),
                        balance.remainingMinutes());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contract.id());
        payload.put("contractCode", contract.code());
        payload.put("contractName", contract.name());
        payload.put("periodLabel", balance.label());
        payload.put("threshold", threshold);
        payload.put("consumptionRate", balance.consumptionRate());
        payload.put("remainingMinutes", balance.remainingMinutes());

        return new NotificationCommand(
                recipients,
                NotificationType.CONTRACT_USAGE,
                severityFor(threshold),
                text.title(),
                text.body(),
                renderer.payload(payload),
                ENTITY_TYPE,
                balance.periodId(),
                NotificationCommand.sameKey(
                        dedupeKeyBuilder.consumption(balance.periodId(), threshold)));
    }

    private NotificationCommand overageCommand(
            PeriodBalanceResponse balance, ContractRefResponse contract, Set<UUID> recipients) {
        var text = renderer.overage(contract.name(), balance.label(), balance.overageMinutes());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contract.id());
        payload.put("contractCode", contract.code());
        payload.put("contractName", contract.name());
        payload.put("periodLabel", balance.label());
        payload.put("overageMinutes", balance.overageMinutes());

        return new NotificationCommand(
                recipients,
                NotificationType.CONTRACT_OVERAGE,
                NotificationSeverity.CRITICAL, // NT-04: impacto financeiro direto
                text.title(),
                text.body(),
                renderer.payload(payload),
                ENTITY_TYPE,
                balance.periodId(),
                NotificationCommand.sameKey(dedupeKeyBuilder.overage(balance.periodId())));
    }

    /**
     * §6.1: {@code INFO} até 80%, {@code WARNING} até 100%, {@code CRITICAL} daí em diante.
     *
     * <p>CE-N-03: um limiar configurado acima de 100% — 150%, por exemplo — é suportado e continua
     * crítico. A faixa é sobre o valor do limiar, não sobre uma lista fixa de três.
     */
    private NotificationSeverity severityFor(int threshold) {
        if (threshold >= CRITICAL_THRESHOLD) {
            return NotificationSeverity.CRITICAL;
        }
        return threshold >= WARNING_THRESHOLD
                ? NotificationSeverity.WARNING
                : NotificationSeverity.INFO;
    }
}
