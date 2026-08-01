package com.devtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.notification.domain.NotificationSeverity;
import com.devtime.notification.domain.NotificationType;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Catálogo de tipos (§6 e §9.1 de notifications.md).
 *
 * <p>A propriedade mais importante verificada aqui é a de §9.1: <b>notificação crítica não pode ser
 * silenciada</b>. Um contrato excedido tem impacto financeiro direto e um anexo infectado é
 * incidente de segurança — permitir silenciá-los contrariaria o propósito do produto.
 */
class NotificationTypeTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationType.class)
    @DisplayName("§9.1/CA-03: todo tipo CRITICAL é não silenciável, sem exceção")
    void criticalTypesAreNeverMutable(NotificationType type) {
        if (type.getDefaultSeverity() == NotificationSeverity.CRITICAL) {
            assertThat(type.isCanMute())
                    .as("%s é crítica e não pode ser silenciada", type)
                    .isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationType.class)
    @DisplayName("§9.1: todo tipo possui rótulo exibível na tela de preferências")
    void everyTypeHasALabel(NotificationType type) {
        assertThat(type.getLabel()).isNotBlank();
    }

    @Test
    @DisplayName("NT-04: a severidade crítica é reservada a impacto financeiro e segurança")
    void criticalIsReservedToFinancialAndSecurity() {
        assertThat(
                        Arrays.stream(NotificationType.values())
                                .filter(
                                        type ->
                                                type.getDefaultSeverity()
                                                        == NotificationSeverity.CRITICAL)
                                .toList())
                .as("inflacionar a severidade destrói o seu significado")
                .containsExactlyInAnyOrder(
                        NotificationType.CONTRACT_OVERAGE, NotificationType.ATTACHMENT_INFECTED);
    }

    @Test
    @DisplayName("CP-05: não existe tipo fixo por limiar — o consumo é um tipo só")
    void consumptionIsASingleTypeDrivenByThresholds() {
        // Um contrato com [70, 90] precisa alertar em 70 e 90; tipos fixos CONTRACT_USAGE_50/80/100
        // divergiriam do painel do mesmo contrato.
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name).toList())
                .contains("CONTRACT_USAGE")
                .doesNotContain("CONTRACT_USAGE_50", "CONTRACT_USAGE_80", "CONTRACT_USAGE_100");
    }

    @Test
    @DisplayName("§17.1: a resolução por nome é tolerante a caixa e recusa desconhecidos")
    void byNameIsCaseInsensitive() {
        assertThat(NotificationType.byName("timer_abandoned"))
                .contains(NotificationType.TIMER_ABANDONED);
        assertThat(NotificationType.byName("  PERIOD_CLOSED "))
                .contains(NotificationType.PERIOD_CLOSED);
        assertThat(NotificationType.byName("INEXISTENTE")).isEmpty();
        assertThat(NotificationType.byName(null)).isEmpty();
    }
}
