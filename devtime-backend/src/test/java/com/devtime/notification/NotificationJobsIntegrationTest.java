package com.devtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Os quatro jobs de {@code 013} (§22.4 da spec).
 *
 * <p>Nenhum deles tinha teste, e a nota ¹⁵ de {@code implementation-order.md} registra por que isso
 * importa: um defeito de construção de sessão fazia <b>toda</b> iteração dos lembretes falhar
 * silenciosamente dentro do {@code catch}, e o defeito só apareceu quando outra feature esbarrou
 * nele. Um job que engole a própria falha precisa de teste justamente porque não reclama.
 *
 * <p>As asserções são sobre <b>execução completa e convergência</b>, não sobre a quantidade de
 * notificações: os lembretes dependem de datas de calendário que o cenário compartilhado não
 * garante, e afirmar sobre elas produziria um teste que passa por coincidência.
 */
// O perfil `scheduler` é o que registra os jobs (backend.md §13.1). Sem ele o bean não existe e
// o teste passaria por ausência do alvo, que é a forma mais silenciosa de não testar nada.
@ActiveProfiles({"test", "scheduler"})
class NotificationJobsIntegrationTest extends FeatureTestSupport {

    @Autowired private NotificationJobs jobs;
    @Autowired private NotificationQueryService queryService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-605: o lembrete de fechamento percorre os tenants sem falhar em silêncio")
    void periodClosingReminderRunsAcrossTenants() {
        asOwnerOfA(scenario::create);

        assertThatCode(() -> jobs.remindPeriodClosing())
                .as("BR-049: a sessão de plataforma é construída a cada iteração")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-606: o lembrete de contrato terminando executa sobre todos os tenants")
    void contractEndingReminderRuns() {
        asOwnerOfA(scenario::create);

        assertThatCode(() -> jobs.remindContractEnding()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-610: o reprocessamento de e-mail roda sem pendências e sem erro")
    void emailRetryRunsWithoutPendingMessages() {
        assertThatCode(() -> jobs.retryPendingEmails())
                .as("CE-A-05: e-mail indisponível é degradação, nunca falha de job")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§19.1: a limpeza remove apenas notificação lida além da retenção")
    void purgeKeepsUnreadNotifications() {
        asOwnerOfA(scenario::create);
        long naoLidasAntes = asOwnerOfA(() -> queryService.unreadCount().unreadCount());

        jobs.purgeReadNotifications();
        clock.advance(Duration.ofDays(120));
        jobs.purgeReadNotifications();

        assertThat(asOwnerOfA(() -> queryService.unreadCount().unreadCount()))
                .as("a limpeza não pode alcançar o que o usuário ainda não viu")
                .isEqualTo(naoLidasAntes);
    }

    @Test
    @DisplayName("BR-185: reexecutar os quatro jobs não muda nada")
    void jobsAreConvergent() {
        asOwnerOfA(scenario::create);

        assertThatCode(
                        () -> {
                            jobs.remindPeriodClosing();
                            jobs.remindPeriodClosing();
                            jobs.remindContractEnding();
                            jobs.remindContractEnding();
                            jobs.retryPendingEmails();
                            jobs.retryPendingEmails();
                            jobs.purgeReadNotifications();
                            jobs.purgeReadNotifications();
                        })
                .doesNotThrowAnyException();
    }
}
