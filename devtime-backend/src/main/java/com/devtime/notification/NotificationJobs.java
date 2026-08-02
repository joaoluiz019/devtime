package com.devtime.notification;

import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.ContractService;
import com.devtime.contract.dto.ContractResponses.ContractReminderView;
import com.devtime.contract.dto.ContractResponses.PeriodReminderView;
import com.devtime.notification.domain.Notification;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.shared.time.TenantClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jobs da feature 013 (spec §22.4).
 *
 * <p>Reunidos em uma classe porque compartilham a mesma natureza — varredura por predicado sobre o
 * estado atual, sem parâmetro — e o mesmo arranjo de contexto por tenant. É o critério já usado em
 * {@code AuthCleanupJobs} e {@code PeriodMaintenanceJobs}.
 *
 * <p><b>Os lembretes são idempotentes pelo {@code dedupeKey}, e não por controle próprio</b>
 * (CE-15, CX-24). Rodar duas vezes no mesmo dia não duplica notificação: a segunda inserção é
 * rejeitada pelo índice único. É a mesma garantia que protege a avaliação de limiares em rajada — e
 * é o que permite que estes jobs não guardem estado algum.
 *
 * <p>BR-049: os jobs percorrem todos os tenants e <b>definem o contexto a cada iteração</b>. Sem
 * isso, a resolução de destinatários usaria a organização errada — ou nenhuma.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class NotificationJobs {

    /** RN-605. */
    private static final int PERIOD_CLOSING_DAYS_AHEAD = 3;

    /** RN-606. */
    private static final int CONTRACT_ENDING_DAYS_AHEAD = 15;

    /** RN-609. */
    private static final Duration RETENTION_AFTER_READ = Duration.ofDays(90);

    /** RN-610: lote por execução, para que uma fila acumulada não monopolize o job (BR-186). */
    private static final int EMAIL_RETRY_BATCH = 100;

    private final NotificationRepository repository;
    private final NotificationService notificationService;
    private final EmailDispatchService emailDispatchService;
    private final RecipientResolver recipientResolver;
    private final NotificationTemplateRenderer renderer;
    private final DedupeKeyBuilder dedupeKeyBuilder;
    private final ContractPeriodService contractPeriodService;
    private final ContractService contractService;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    /** RN-605: {@code PERIOD_CLOSING} 3 dias antes do fim do período. */
    @Scheduled(cron = "0 0 8 * * *")
    @SchedulerLock(name = "periodClosingReminder", lockAtMostFor = "PT15M")
    @Transactional
    public void remindPeriodClosing() {
        LocalDate target = clock.today().plusDays(PERIOD_CLOSING_DAYS_AHEAD);
        List<PeriodReminderView> periods = contractPeriodService.findEndingOn(target);

        int notified = 0;
        for (PeriodReminderView period : periods) {
            notified += inTenant(period.tenantId(), () -> notifyPeriodClosing(period));
        }
        log.info(
                "lembrete de fechamento concluído períodos={} notificações={}",
                periods.size(),
                notified);
    }

    /** RN-606: {@code CONTRACT_ENDING} 15 dias antes do fim do contrato. */
    @Scheduled(cron = "0 15 8 * * *")
    @SchedulerLock(name = "contractEndingReminder", lockAtMostFor = "PT15M")
    @Transactional
    public void remindContractEnding() {
        LocalDate target = clock.today().plusDays(CONTRACT_ENDING_DAYS_AHEAD);
        List<ContractReminderView> contracts = contractService.findEndingOn(target);

        int notified = 0;
        for (ContractReminderView contract : contracts) {
            notified += inTenant(contract.tenantId(), () -> notifyContractEnding(contract));
        }
        log.info(
                "lembrete de contrato terminando concluído contratos={} notificações={}",
                contracts.size(),
                notified);
    }

    /**
     * RN-610: reprocessa e-mails pendentes, até três tentativas.
     *
     * <p>O <i>backoff</i> é o próprio intervalo do job — cinco minutos entre execuções —, e não uma
     * espera dentro do processo: uma tentativa a cada 5, 10 e 15 minutos dá ao provedor tempo de se
     * recuperar sem manter thread bloqueada.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "emailRetry", lockAtMostFor = "PT10M")
    @Transactional
    public void retryPendingEmails() {
        List<Notification> pending =
                repository.findPendingEmail(PageRequest.of(0, EMAIL_RETRY_BATCH));
        if (pending.isEmpty()) {
            return;
        }
        long delivered = pending.stream().filter(emailDispatchService::retry).count();
        log.info(
                "reprocessamento de e-mails concluído tentados={} entregues={}",
                pending.size(),
                delivered);
    }

    /**
     * RN-609: remove notificações <b>lidas</b> há mais de 90 dias.
     *
     * <p>CX-17 / CP-14: uma notificação <b>não lida</b> nunca é removida, por mais antiga que seja.
     * Purgar um alerta que ninguém viu esconderia a informação de que ele existiu — o oposto do
     * propósito da central.
     */
    @Scheduled(cron = "0 30 4 * * *")
    @SchedulerLock(name = "notificationCleanup", lockAtMostFor = "PT30M")
    @Transactional
    public void purgeReadNotifications() {
        // CX-16: o corte é estritamente maior — lida há exatamente 90 dias permanece.
        List<Notification> purgeable =
                repository.findPurgeable(clock.now().minus(RETENTION_AFTER_READ));
        if (purgeable.isEmpty()) {
            return;
        }
        int removed = repository.purge(purgeable.stream().map(Notification::getId).toList());
        log.info("limpeza de notificações concluída removidas={}", removed);
    }

    private int notifyPeriodClosing(PeriodReminderView period) {
        Set<UUID> recipients = recipientResolver.forContractEvents(null);
        if (recipients.isEmpty()) {
            return 0; // FA-05
        }
        var text =
                renderer.periodClosing(period.label(), period.endDate(), PERIOD_CLOSING_DAYS_AHEAD);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("periodLabel", period.label());
        payload.put("endDate", period.endDate().toString());
        payload.put("daysRemaining", PERIOD_CLOSING_DAYS_AHEAD);

        return notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.PERIOD_CLOSING,
                        NotificationType.PERIOD_CLOSING.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(payload),
                        "CONTRACT_PERIOD",
                        period.periodId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.periodClosing(period.periodId()))));
    }

    private int notifyContractEnding(ContractReminderView contract) {
        Set<UUID> recipients = recipientResolver.forContractEvents(null);
        if (recipients.isEmpty()) {
            return 0;
        }
        var text =
                renderer.contractEnding(
                        contract.name(), contract.endDate(), CONTRACT_ENDING_DAYS_AHEAD);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractCode", contract.code());
        payload.put("contractName", contract.name());
        payload.put("endDate", contract.endDate().toString());
        payload.put("daysRemaining", CONTRACT_ENDING_DAYS_AHEAD);

        return notificationService.notify(
                new NotificationCommand(
                        recipients,
                        NotificationType.CONTRACT_ENDING,
                        NotificationType.CONTRACT_ENDING.getDefaultSeverity(),
                        text.title(),
                        text.body(),
                        renderer.payload(payload),
                        "CONTRACT",
                        contract.contractId(),
                        NotificationCommand.sameKey(
                                dedupeKeyBuilder.contractEnding(contract.contractId()))));
    }

    /**
     * BR-049 / CE-P-08: define o contexto do tenant na iteração e o limpa ao final.
     *
     * <p>A sessão é sintética, com papel {@code OWNER} — o job age como sistema (CE-S-06) e precisa
     * das permissões de leitura que a resolução de destinatários e a montagem do texto exigem.
     * {@code userId} é nulo de propósito: não existe autor humano, e NT-05 não tem ninguém a
     * excluir.
     *
     * <p>BR-187: a falha em um tenant não interrompe os demais.
     */
    private int inTenant(UUID tenantId, java.util.function.IntSupplier action) {
        var previous = tenantContext.session().orElse(null);
        // Sessão de plataforma sem usuário. Antes construída pelo construtor canônico, que rejeita
        // userId nulo: toda iteração destes jobs falhava e era engolida pelo catch abaixo, deixando
        // os lembretes de RN-605 e RN-606 sem efeito. Defeito corrigido junto com T-004-29.
        tenantContext.set(
                TenantSession.system(tenantId, Role.OWNER, RolePermissions.of(Role.OWNER)));
        try {
            return action.getAsInt();
        } catch (RuntimeException failure) {
            log.error("falha ao notificar tenant tenantId={}", tenantId, failure);
            return 0;
        } finally {
            if (previous == null) {
                tenantContext.clear();
            } else {
                tenantContext.set(previous);
            }
        }
    }
}
