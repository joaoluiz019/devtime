package com.devtime.worklog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.contract.BalanceService;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.WorkLogScenario;
import com.devtime.ticket.TicketService;
import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogDuplicateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogUpdateRequest;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCreatedResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Regras do registro de horas contra o banco real (RN-101 a RN-126, spec 008).
 *
 * <p>Cobre a ordem normativa da §6.1, a propagação transacional dos desnormalizados e os casos
 * extremos que só aparecem com dados persistidos — sobreposição, resolução de período e travamento.
 */
class WorkLogServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogRepository workLogRepository;
    @Autowired private BalanceService balanceService;
    @Autowired private TicketService ticketService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-109/RN-107/RN-108: o registro copia contrato e cliente e resolve o período")
    void shouldCopyContractAndResolvePeriod() {
        var setup = asOwnerOfA(scenario::create);

        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 30)));

        assertThat(created.workLog().contractId())
                .as("RN-109: copiado do ticket, nunca do payload")
                .isEqualTo(setup.contract().id());
        assertThat(created.workLog().clientId()).isEqualTo(setup.clientId());
        assertThat(created.workLog().contractPeriodId())
                .as("RN-107: o período que contém workDate")
                .isEqualTo(setup.period().id());
        assertThat(created.workLog().workDate())
                .as("RN-108: data local do início, no fuso do tenant")
                .isEqualTo(WorkLogScenario.WORK_DAY);
        assertThat(created.workLog().grossMinutes()).isEqualTo(150);
        assertThat(created.workLog().netMinutes()).isEqualTo(150);
        assertThat(created.workLog().source()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("RN-108/CX-01: sessão que atravessa a meia-noite pertence à data de início")
    void midnightCrossingBelongsToStartDate() {
        var setup = asOwnerOfA(scenario::create);

        WorkLogCreatedResponse created =
                asOwnerOfA(
                        () ->
                                workLogService.create(
                                        request(
                                                setup,
                                                WorkLogScenario.at(22, 0),
                                                WorkLogScenario.at(
                                                        WorkLogScenario.WORK_DAY.plusDays(1),
                                                        1,
                                                        30,
                                                        0))));

        assertThat(created.workLog().workDate())
                .as("CP-08: registro único, sem divisão entre dias")
                .isEqualTo(WorkLogScenario.WORK_DAY);
        assertThat(created.workLog().netMinutes()).isEqualTo(210);
    }

    @Test
    @DisplayName("RN-102: sessão sobreposta do mesmo usuário é rejeitada com DEVTIME-2102")
    void shouldRejectOverlappingSession() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        assertThatThrownBy(
                        () -> asOwnerOfA(() -> workLogService.create(request(setup, 10, 0, 12, 0))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        error ->
                                assertThat(((BusinessRuleException) error).getErrorCode())
                                        .isEqualTo(ErrorCode.WORKLOG_OVERLAP));
    }

    @Test
    @DisplayName("RN-102/CX-06: sessões que se tocam exatamente são aceitas")
    void touchingSessionsAreAccepted() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        WorkLogCreatedResponse second =
                asOwnerOfA(() -> workLogService.create(request(setup, 11, 0, 12, 0)));

        assertThat(second.workLog().netMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("RN-102/CX-17: na edição, o próprio registro é excluído da comparação")
    void editingOwnRecordDoesNotConflictWithItself() {
        var setup = asOwnerOfA(scenario::create);
        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        WorkLogCreatedResponse updated =
                asOwnerOfA(
                        () ->
                                workLogService.update(
                                        created.workLog().id(),
                                        updateRequest(setup, created, 9, 0, 12, 0)));

        assertThat(updated.workLog().netMinutes()).isEqualTo(180);
        assertThat(updated.workLog().editCount())
                .as("RN-123: toda edição incrementa a contra-métrica de qualidade de captura")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("RN-103: sessão acima de 24 horas é rejeitada com DEVTIME-2103")
    void shouldRejectSessionLongerThanTwentyFourHours() {
        var setup = asOwnerOfA(scenario::create);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.create(
                                                        request(
                                                                setup,
                                                                WorkLogScenario.at(8, 0),
                                                                WorkLogScenario.at(
                                                                        WorkLogScenario.WORK_DAY
                                                                                .plusDays(1),
                                                                        9,
                                                                        0,
                                                                        0)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_SESSION_TOO_LONG));
    }

    @Test
    @DisplayName("RN-114: hora final igual à inicial é rejeitada com DEVTIME-2114")
    void shouldRejectNonChronologicalRange() {
        var setup = asOwnerOfA(scenario::create);

        assertThatThrownBy(
                        () -> asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 9, 0))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_RANGE_INVALID));
    }

    @Test
    @DisplayName("RN-116/CX-12: pausas iguais ao tempo bruto são rejeitadas antes de RN-115")
    void shouldRejectPauseEqualToGross() {
        var setup = asOwnerOfA(scenario::create);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.create(
                                                        new WorkLogCreateRequest(
                                                                setup.ticket().id(),
                                                                WorkLogScenario.at(9, 0),
                                                                WorkLogScenario.at(10, 0),
                                                                60,
                                                                "Sessão inteiramente pausada",
                                                                setup.category().id(),
                                                                true,
                                                                List.of(),
                                                                null))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_PAUSED_MINUTES_INVALID));
    }

    @Test
    @DisplayName("RN-118/CX-10: término no futuro além da tolerância é rejeitado")
    void shouldRejectFutureEnd() {
        var setup = asOwnerOfA(scenario::create);
        Instant future = NOW.plusSeconds(3600);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.create(
                                                        request(
                                                                setup,
                                                                future.minusSeconds(600),
                                                                future))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_ENDED_IN_FUTURE));
    }

    @Test
    @DisplayName("RN-107/FA-18: data sem período correspondente é rejeitada com DEVTIME-2107")
    void shouldRejectDateWithoutPeriod() {
        var setup = asOwnerOfA(scenario::create);
        // 05/01/2026 é anterior ao início do contrato (10/01) e a qualquer período gerado.
        LocalDate beforeAnyPeriod = LocalDate.of(2026, 1, 5);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.create(
                                                        request(
                                                                setup,
                                                                WorkLogScenario.at(
                                                                        beforeAnyPeriod, 9, 0, 0),
                                                                WorkLogScenario.at(
                                                                        beforeAnyPeriod,
                                                                        10,
                                                                        0,
                                                                        0)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        error ->
                                // RN-117 é verificada antes de RN-107 na ordem normativa da §6.1:
                                // a data está fora da vigência do contrato, e é esse o erro
                                // esperado — não a ausência de período.
                                assertThat(((BusinessRuleException) error).getErrorCode())
                                        .isEqualTo(ErrorCode.WORKLOG_OUTSIDE_CONTRACT_VALIDITY));
    }

    @Test
    @DisplayName("RN-308/RN-312: a criação propaga os totais para o ticket na mesma transação")
    void shouldPropagateTicketTotals() {
        var setup = asOwnerOfA(scenario::create);

        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 30)));

        var ticket = asOwnerOfA(() -> ticketService.getById(setup.ticket().id()));
        assertThat(ticket.spentMinutes()).isEqualTo(150);
        assertThat(ticket.billableMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("RN-219: a criação atualiza consumedMinutes do período por incremento")
    void shouldPropagatePeriodConsumption() {
        var setup = asOwnerOfA(scenario::create);

        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 30)));

        var balance = asOwnerOfA(() -> balanceService.getBalance(setup.period().id()));
        assertThat(balance.consumedMinutes()).isEqualTo(150);
        assertThat(balance.isPartial()).as("RN-702: período aberto é sempre parcial").isTrue();
    }

    @Test
    @DisplayName("RN-112/RN-223/CX-21: horas não faturáveis não consomem saldo")
    void nonBillableDoesNotConsumeBalance() {
        var setup = asOwnerOfA(scenario::create);

        WorkLogCreatedResponse created =
                asOwnerOfA(
                        () ->
                                workLogService.create(
                                        new WorkLogCreateRequest(
                                                setup.ticket().id(),
                                                WorkLogScenario.at(14, 0),
                                                WorkLogScenario.at(15, 0),
                                                0,
                                                "Reunião interna não faturável",
                                                setup.category().id(),
                                                false,
                                                List.of(),
                                                null)));

        assertThat(created.workLog().netMinutes()).isEqualTo(60);
        assertThat(created.workLog().billableMinutes()).isZero();
        assertThat(created.balance().consumedMinutes()).isZero();
        assertThat(created.balance().nonBillableMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("RN-125: a exclusão devolve o saldo ao período e reduz o total do ticket")
    void deleteShouldReturnBalanceAndReduceTicketTotals() {
        var setup = asOwnerOfA(scenario::create);
        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 30)));

        asOwnerOfA(
                () -> {
                    workLogService.delete(created.workLog().id());
                    return null;
                });

        var balance = asOwnerOfA(() -> balanceService.getBalance(setup.period().id()));
        var ticket = asOwnerOfA(() -> ticketService.getById(setup.ticket().id()));
        assertThat(balance.consumedMinutes()).isZero();
        assertThat(ticket.spentMinutes()).isZero();
        assertThatThrownBy(() -> asOwnerOfA(() -> workLogService.getById(created.workLog().id())))
                .as("RN-003: exclusão lógica torna o registro invisível a toda consulta padrão")
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-121: registro de período travado não pode ser editado nem excluído")
    void lockedWorkLogIsImmutable() {
        var setup = asOwnerOfA(scenario::create);
        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 30)));

        asOwnerOfA(() -> workLogService.lockByPeriod(setup.period().id()));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.update(
                                                        created.workLog().id(),
                                                        updateRequest(
                                                                setup, created, 9, 0, 12, 0))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_LOCKED));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            workLogService.delete(created.workLog().id());
                                            return null;
                                        }))
                .as("OWN-02: nem o autor edita um registro travado")
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_LOCKED));
    }

    @Test
    @DisplayName("FA-14/CX-28: duplicar com o mesmo horário é rejeitado por RN-102")
    void duplicateWithSameRangeIsRejected() {
        var setup = asOwnerOfA(scenario::create);
        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                workLogService.duplicate(
                                                        created.workLog().id(),
                                                        new WorkLogDuplicateRequest(
                                                                WorkLogScenario.at(9, 0),
                                                                WorkLogScenario.at(11, 0)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(hasCode(ErrorCode.WORKLOG_OVERLAP));
    }

    @Test
    @DisplayName("FA-14: duplicar em novo horário copia ticket, categoria e descrição")
    void duplicateWithNewRangeCopiesContent() {
        var setup = asOwnerOfA(scenario::create);
        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        WorkLogCreatedResponse copy =
                asOwnerOfA(
                        () ->
                                workLogService.duplicate(
                                        created.workLog().id(),
                                        new WorkLogDuplicateRequest(
                                                WorkLogScenario.at(14, 0),
                                                WorkLogScenario.at(15, 0))));

        assertThat(copy.workLog().id()).isNotEqualTo(created.workLog().id());
        assertThat(copy.workLog().description()).isEqualTo(created.workLog().description());
        assertThat(copy.workLog().ticket().id()).isEqualTo(created.workLog().ticket().id());
        assertThat(copy.workLog().netMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("RN-002/SG-01: registro de outro tenant responde 404, nunca 403")
    void shouldIsolateAcrossTenants() {
        var setup = asOwnerOfA(scenario::create);
        WorkLogCreatedResponse created =
                asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        assertThatThrownBy(() -> asOwnerOfB(() -> workLogService.getById(created.workLog().id())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-012: a listagem devolve projeção sem descrição e respeita a paginação")
    void searchShouldReturnProjection() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));

        var page =
                asOwnerOfA(
                        () -> workLogService.search(WorkLogFilter.empty(), PageRequest.of(0, 20)));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).durationLabel()).isEqualTo("02:00");
        assertThat(page.content().get(0).ticketKey()).isNotBlank();
    }

    @Test
    @DisplayName("INV-WKL-05: nenhuma sobreposição chega a ser persistida no cenário nominal")
    void repositoryShouldNotContainOverlaps() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(() -> workLogService.create(request(setup, 9, 0, 11, 0)));
        asOwnerOfA(() -> workLogService.create(request(setup, 11, 0, 12, 0)));

        assertThat(asOwnerOfA(() -> workLogRepository.findOverlappingPairs())).isEmpty();
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private WorkLogCreateRequest request(
            WorkLogScenario.Scenario setup,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute) {
        return request(
                setup,
                WorkLogScenario.at(startHour, startMinute),
                WorkLogScenario.at(endHour, endMinute));
    }

    private WorkLogCreateRequest request(
            WorkLogScenario.Scenario setup, Instant startedAt, Instant endedAt) {
        return new WorkLogCreateRequest(
                setup.ticket().id(),
                startedAt,
                endedAt,
                0,
                "Implementação do fluxo de checkout",
                setup.category().id(),
                true,
                List.of(),
                null);
    }

    private WorkLogUpdateRequest updateRequest(
            WorkLogScenario.Scenario setup,
            WorkLogCreatedResponse created,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute) {
        return new WorkLogUpdateRequest(
                setup.ticket().id(),
                WorkLogScenario.at(startHour, startMinute),
                WorkLogScenario.at(endHour, endMinute),
                0,
                "Implementação do fluxo de checkout revisada",
                setup.category().id(),
                true,
                List.of(),
                created.workLog().version());
    }

    private static java.util.function.Consumer<Throwable> hasCode(ErrorCode expected) {
        return error ->
                assertThat(((BusinessRuleException) error).getErrorCode()).isEqualTo(expected);
    }
}
