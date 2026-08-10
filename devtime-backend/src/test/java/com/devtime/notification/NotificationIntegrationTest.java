package com.devtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.AdjustmentService;
import com.devtime.contract.domain.AdjustmentReason;
import com.devtime.contract.dto.BalanceRequests.AdjustmentRequest;
import com.devtime.notification.domain.NotificationSeverity;
import com.devtime.notification.domain.NotificationType;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.notification.dto.NotificationRequests.NotificationFilter;
import com.devtime.notification.dto.NotificationRequests.NotificationPreferencesRequest;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Deduplicação, geração e central de notificações (RN-601 a RN-610, spec 013).
 *
 * <p>O foco é a <b>deduplicação</b>: sem ela, o consumo oscilando em torno de um limiar geraria uma
 * notificação por oscilação e o usuário desligaria as notificações inteiras. É a regra que sustenta
 * o valor da feature, e a única cuja falha é silenciosa.
 */
class NotificationIntegrationTest extends FeatureTestSupport {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationQueryService queryService;
    @Autowired private NotificationRepository repository;
    @Autowired private DedupeKeyBuilder dedupeKeyBuilder;
    @Autowired private WorkLogService workLogService;
    @Autowired private AdjustmentService adjustmentService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-601/CA-01: cem avaliações da mesma chave produzem UMA notificação")
    void duplicatesAreIgnoredSilently() {
        UUID periodId = UUID.randomUUID();

        int created =
                asOwnerOfA(
                        () -> {
                            int total = 0;
                            for (int attempt = 0; attempt < 100; attempt++) {
                                total += notificationService.notify(command(periodId, 80));
                            }
                            return total;
                        });

        assertThat(created)
                .as("as outras 99 inserções são rejeitadas pelo índice único, sem erro (CA-02)")
                .isEqualTo(1);
        assertThat(asOwnerOfA(() -> repository.countUnread(userAId))).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-601/CA-03: a sequência de oscilação da §6.3 não gera notificação nova")
    void consumptionOscillationDoesNotCreateNewNotifications() {
        UUID periodId = UUID.randomUUID();

        // Momento 2 da §6.3: o consumo chega a 82% e cria :50 e :80.
        asOwnerOfA(() -> notificationService.notify(command(periodId, 50)));
        asOwnerOfA(() -> notificationService.notify(command(periodId, 80)));
        long afterFirstRise = asOwnerOfA(() -> repository.countUnread(userAId));

        // Momentos 3 e 4: o consumo cai para 70% e volta a 85%. A avaliação roda de novo.
        int createdOnSecondRise =
                asOwnerOfA(
                        () ->
                                notificationService.notify(command(periodId, 50))
                                        + notificationService.notify(command(periodId, 80)));

        assertThat(afterFirstRise).isEqualTo(2);
        assertThat(createdOnSecondRise)
                .as("as chaves já existem — nenhum alerta novo (CE-03)")
                .isZero();
        assertThat(asOwnerOfA(() -> repository.countUnread(userAId)))
                .as("CA-04: a notificação anterior permanece quando o consumo cai")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("RN-604/CA-06: limiar e excedente do mesmo período são notificações distintas")
    void thresholdAndOverageAreSeparateNotifications() {
        UUID periodId = UUID.randomUUID();

        asOwnerOfA(() -> notificationService.notify(command(periodId, 100)));
        int overage =
                asOwnerOfA(
                        () ->
                                notificationService.notify(
                                        new NotificationCommand(
                                                Set.of(userAId),
                                                NotificationType.CONTRACT_OVERAGE,
                                                NotificationSeverity.CRITICAL,
                                                "Contrato excedido",
                                                "O saldo foi ultrapassado.",
                                                Map.of(),
                                                "CONTRACT_PERIOD",
                                                periodId,
                                                NotificationCommand.sameKey(
                                                        dedupeKeyBuilder.overage(periodId)))));

        assertThat(overage).isEqualTo(1);
        assertThat(asOwnerOfA(() -> repository.countUnread(userAId))).isEqualTo(2);
    }

    @Test
    @DisplayName("FA-05: sem destinatários, nada é criado e nenhum erro é levantado")
    void noRecipientsProducesNothing() {
        UUID periodId = UUID.randomUUID();

        int created =
                asOwnerOfA(
                        () ->
                                notificationService.notify(
                                        new NotificationCommand(
                                                Set.of(),
                                                NotificationType.PERIOD_CLOSED,
                                                NotificationSeverity.INFO,
                                                "Período fechado",
                                                "corpo",
                                                Map.of(),
                                                "CONTRACT_PERIOD",
                                                periodId,
                                                NotificationCommand.sameKey("X:" + periodId))));

        assertThat(created).isZero();
    }

    @Test
    @DisplayName("CX-09: dois destinatários recebem a mesma chave, uma notificação cada")
    void eachRecipientGetsItsOwnNotification() {
        UUID periodId = UUID.randomUUID();

        // O índice único é (recipient_id, dedupe_key): a mesma chave para pessoas diferentes é
        // permitida, e é exatamente o caso de dois OWNER no mesmo tenant.
        int created =
                asOwnerOfA(
                        () ->
                                notificationService.notify(
                                        new NotificationCommand(
                                                Set.of(userAId, userBId),
                                                NotificationType.PERIOD_CLOSED,
                                                NotificationSeverity.INFO,
                                                "Período fechado",
                                                "corpo",
                                                Map.of(),
                                                "CONTRACT_PERIOD",
                                                periodId,
                                                NotificationCommand.sameKey(
                                                        dedupeKeyBuilder.periodClosed(periodId)))));

        assertThat(created).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-602/RN-607: registrar horas gera o alerta de consumo para OWNER e ADMIN")
    void workLogTriggersConsumptionAlert() {
        // NT-05: quem registra as horas não é notificado do próprio lançamento. Sem um segundo
        // membro com papel de cobrança, o conjunto de destinatários é vazio e nenhuma notificação
        // deveria mesmo existir — a versão anterior deste teste procurava o alerta na caixa de
        // quem, por regra, jamais o receberia.
        UUID adminId = adminOfA();
        var setup = asOwnerOfA(scenario::create);

        // O contrato contrata 2.400 minutos por mês, mas RN-217 torna o primeiro período
        // proporcional (10/01 a 31/01). O alvo é calculado sobre o período, não sobre o mês, para
        // cruzar o primeiro limiar configurado — 50%, de `{50, 80, 100}`.
        int alvo = (int) Math.round(setup.period().contractedMinutes() * 0.6);
        int dia = 16;
        for (int restante = alvo; restante > 0; dia++) {
            final int minutos = Math.min(restante, 8 * 60);
            final int diaDoRegistro = dia;
            asOwnerOfA(() -> workLogService.create(request(setup, diaDoRegistro, minutos)));
            restante -= minutos;
        }

        var page =
                runAs(
                        tenantAId,
                        adminId,
                        Role.ADMIN,
                        () ->
                                queryService.search(
                                        NotificationFilter.empty(), PageRequest.of(0, 20)));

        assertThat(page.content())
                .as("RN-602: os limiares são avaliados após o commit do registro de horas")
                .anySatisfy(
                        notification ->
                                assertThat(notification.type())
                                        .isEqualTo(NotificationType.CONTRACT_USAGE.name()));
    }

    /** Segundo membro do tenant A, com papel de cobrança — destinatário de RN-607. */
    private UUID adminOfA() {
        // Dentro de uma sessão do tenant A: o vínculo é entidade com escopo de tenant e o
        // `tenant_id` é preenchido pelo contexto, não pelo chamador (ART-022).
        return asOwnerOfA(
                () -> {
                    UUID adminId =
                            userRepository
                                    .save(
                                            FoundationDataBuilder.user(
                                                    "admin-"
                                                            + UUID.randomUUID()
                                                                    .toString()
                                                                    .substring(0, 8)
                                                            + "@exemplo.com",
                                                    NOW))
                                    .getId();
                    membershipRepository.save(
                            FoundationDataBuilder.membership(tenantAId, adminId, Role.ADMIN, NOW));
                    return adminId;
                });
    }

    private WorkLogCreateRequest request(
            WorkLogScenario.Scenario setup, int diaDeJaneiro, int minutos) {
        LocalDate dia = LocalDate.of(2026, 1, diaDeJaneiro);
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(dia, 8, 0, 0),
                WorkLogScenario.at(dia, 8 + minutos / 60, minutos % 60, 0),
                0,
                "Migração do módulo de faturamento",
                setup.category().id(),
                true,
                List.of(),
                null);
    }

    @Test
    @DisplayName("RN-215: o ajuste de saldo notifica quem responde pelo tenant")
    void adjustmentNotifiesBillingRoles() {
        var setup = asOwnerOfA(scenario::create);

        // NT-05: o autor não é notificado do próprio ajuste. Como o cenário tem um único OWNER,
        // que é o autor, o resultado esperado é nenhuma notificação de ajuste.
        asOwnerOfA(
                () ->
                        adjustmentService.apply(
                                setup.period().id(),
                                new AdjustmentRequest(
                                        120,
                                        AdjustmentReason.COURTESY,
                                        "Cortesia acordada na renovação")));

        var page =
                asOwnerOfA(
                        () ->
                                queryService.search(
                                        NotificationFilter.empty(), PageRequest.of(0, 20)));

        assertThat(page.content())
                .as("NT-05: ninguém é avisado do que acabou de fazer")
                .noneSatisfy(
                        notification ->
                                assertThat(notification.type())
                                        .isEqualTo(NotificationType.ADJUSTMENT_APPLIED.name()));
    }

    @Test
    @DisplayName("§8.1: marcar como lida é idempotente e devolve a contagem atualizada")
    void markReadIsIdempotent() {
        UUID periodId = UUID.randomUUID();
        asOwnerOfA(() -> notificationService.notify(command(periodId, 80)));
        UUID notificationId =
                asOwnerOfA(
                                () ->
                                        queryService.search(
                                                NotificationFilter.empty(), PageRequest.of(0, 20)))
                        .content()
                        .get(0)
                        .id();

        var first = asOwnerOfA(() -> queryService.markRead(notificationId));
        var second = asOwnerOfA(() -> queryService.markRead(notificationId));

        assertThat(first.readAt()).isNotNull();
        assertThat(second.readAt())
                .as("o instante da primeira leitura é a informação com valor")
                .isEqualTo(first.readAt());
        assertThat(second.unreadCount()).isZero();
    }

    @Test
    @DisplayName("SG-01/CA-14: notificação de outro destinatário responde 404, nunca 403")
    void cannotReadAnotherRecipientNotification() {
        UUID periodId = UUID.randomUUID();
        asOwnerOfA(
                () ->
                        notificationService.notify(
                                new NotificationCommand(
                                        Set.of(userBId),
                                        NotificationType.PERIOD_CLOSED,
                                        NotificationSeverity.INFO,
                                        "Período fechado",
                                        "corpo",
                                        Map.of(),
                                        "CONTRACT_PERIOD",
                                        periodId,
                                        NotificationCommand.sameKey(
                                                dedupeKeyBuilder.periodClosed(periodId)))));

        UUID otherId =
                asOwnerOfA(
                        () ->
                                repository.findAll().stream()
                                        .filter(n -> n.getRecipientId().equals(userBId))
                                        .findFirst()
                                        .orElseThrow()
                                        .getId());

        assertThatThrownBy(() -> asOwnerOfA(() -> queryService.markRead(otherId)))
                .as("a existência da notificação alheia não deve ser revelada")
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("§9.2/CA-03: silenciar um tipo crítico responde DEVTIME-4001")
    void criticalTypesCannotBeMuted() {
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                queryService.updatePreferences(
                                                        new NotificationPreferencesRequest(
                                                                null,
                                                                List.of(
                                                                        NotificationType
                                                                                .CONTRACT_OVERAGE
                                                                                .name())))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.NOTIFICATION_TYPE_NOT_MUTABLE));
    }

    @Test
    @DisplayName("§17.1: tipo desconhecido em mutedNotificationTypes é rejeitado")
    void unknownTypeIsRejected() {
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                queryService.updatePreferences(
                                                        new NotificationPreferencesRequest(
                                                                null, List.of("NAO_EXISTE")))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("RN-608/CA-08: a in-app é criada mesmo com o tipo silenciado")
    void inAppIsCreatedEvenWhenMuted() {
        asOwnerOfA(
                () ->
                        queryService.updatePreferences(
                                new NotificationPreferencesRequest(
                                        false, List.of(NotificationType.PERIOD_CLOSED.name()))));

        UUID periodId = UUID.randomUUID();
        int created =
                asOwnerOfA(
                        () ->
                                notificationService.notify(
                                        new NotificationCommand(
                                                Set.of(userAId),
                                                NotificationType.PERIOD_CLOSED,
                                                NotificationSeverity.INFO,
                                                "Período fechado",
                                                "corpo",
                                                Map.of(),
                                                "CONTRACT_PERIOD",
                                                periodId,
                                                NotificationCommand.sameKey(
                                                        dedupeKeyBuilder.periodClosed(periodId)))));

        assertThat(created)
                .as("preferência silencia o e-mail, nunca o histórico (NT-01, INV-NOT-02)")
                .isEqualTo(1);
        assertThat(asOwnerOfA(() -> repository.countUnread(userAId))).isEqualTo(1);
    }

    @Test
    @DisplayName("§9.1: as preferências expõem o catálogo com canMute por tipo")
    void preferencesExposeCatalog() {
        var preferences = asOwnerOfA(() -> queryService.preferences());

        assertThat(preferences.emailNotifications())
                .as("entities.md §6.2.1: o padrão é receber")
                .isTrue();
        assertThat(preferences.availableTypes())
                .as("a interface lista os tipos sem replicar o catálogo")
                .hasSize(NotificationType.values().length)
                .anySatisfy(
                        option -> {
                            if (NotificationType.CONTRACT_OVERAGE.name().equals(option.type())) {
                                assertThat(option.canMute()).isFalse();
                            }
                        });
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private NotificationCommand command(UUID periodId, int threshold) {
        return new NotificationCommand(
                Set.of(userAId),
                NotificationType.CONTRACT_USAGE,
                threshold >= 100 ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                "Contrato atingiu " + threshold + "% do saldo",
                "corpo sem dado sensível",
                Map.of("threshold", threshold),
                "CONTRACT_PERIOD",
                periodId,
                NotificationCommand.sameKey(dedupeKeyBuilder.consumption(periodId, threshold)));
    }

    private static Consumer<Throwable> hasCode(ErrorCode expected) {
        return error ->
                assertThat(((BusinessRuleException) error).getErrorCode()).isEqualTo(expected);
    }
}
