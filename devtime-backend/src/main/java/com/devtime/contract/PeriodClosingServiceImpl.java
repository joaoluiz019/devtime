package com.devtime.contract;

import com.devtime.audit.AuditActorNameResolver;
import com.devtime.audit.AuditService;
import com.devtime.client.ClientService;
import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.PeriodAdjustment;
import com.devtime.contract.domain.PeriodBalance;
import com.devtime.contract.domain.PeriodSnapshot;
import com.devtime.contract.domain.PeriodStatus;
import com.devtime.contract.dto.BalanceRequests.ClosePeriodRequest;
import com.devtime.contract.dto.BalanceResponses.ClosePeriodResponse;
import com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry;
import com.devtime.contract.event.BalanceEvents.PeriodClosedEvent;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.tenant.TenantService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sequência atômica de fechamento (RN-241, §6.3 de specs/011).
 *
 * <p>Os sete passos rodam em <b>uma</b> transação, sob lock pessimista adquirido no passo 0.
 *
 * <p><b>Por que lock pessimista e não otimista</b> (CE-ME-08): com <i>optimistic locking</i>, dois
 * fechamentos simultâneos executariam os sete passos e um falharia no commit — mas o passo 3 já
 * teria travado work logs e o passo 4 já teria gerado um snapshot. O lock pessimista impede o
 * segundo de <b>começar</b>.
 *
 * <p><b>Por que o passo 1 é reconciliação e não leitura:</b> {@code consumedMinutes} é
 * desnormalizado, atualizado por incremento em {@code 008}. Uma divergência acumulada por falha
 * produziria um snapshot errado — e o snapshot é <b>definitivo</b>. O fechamento é o último momento
 * em que a correção ainda é possível, então ele recalcula do zero e registra a diferença encontrada
 * (FA-16).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PeriodClosingServiceImpl implements PeriodClosingService {

    private static final String ENTITY_TYPE = "ContractPeriod";

    private final ContractPeriodRepository periodRepository;
    private final ContractRepository contractRepository;
    private final PeriodAdjustmentRepository adjustmentRepository;
    private final PeriodSnapshotRepository snapshotRepository;
    private final BalanceCalculator balanceCalculator;
    private final RolloverCalculator rolloverCalculator;
    private final ClosingGuard closingGuard;
    private final SnapshotBuilder snapshotBuilder;
    private final PeriodMaterializer periodMaterializer;
    private final List<PeriodWorkLogSource> workLogSources;
    private final ClientService clientService;
    private final TenantService tenantService;
    private final AuditActorNameResolver actorNames;
    private final AuditService auditService;
    private final DomainEventPublisher events;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'PERIOD_CLOSE')")
    public ClosePeriodResponse close(UUID periodId, ClosePeriodRequest request) {
        // Passo 0 — lock pessimista antes de qualquer verificação (CE-ME-08, CX-14).
        ContractPeriod period =
                periodRepository
                        .findByIdForUpdate(periodId)
                        .orElseThrow(
                                () -> EntityNotFoundException.of(ContractPeriod.class, periodId));

        closingGuard.assertClosable(period, request.confirmed()); // Passo 0.1 — RN-239
        closingGuard.assertNoActiveTimer(period); // Passo 0.2 — RN-240

        PeriodStatus previousStatus = period.getStatus();
        period.setStatus(PeriodStatus.CLOSING); // Passo 0.3
        auditService.record(
                "PERIOD_CLOSING_STARTED",
                ENTITY_TYPE,
                periodId,
                Map.of("status", previousStatus.name()),
                Map.of("status", PeriodStatus.CLOSING.name()));

        // Passo 1 — reconciliar consumo e ajustes pela agregação real.
        int reconciliationDelta = reconcile(period);

        // Passo 2 — carry-over pela política do contrato (RN-225 a RN-228).
        Contract contract = requireContract(period.getContractId());
        PeriodBalance balance = balanceCalculator.calculate(period);
        int carriedOut =
                rolloverCalculator.carriedOut(
                        contract.getRolloverPolicy(),
                        balance.remainingMinutes(),
                        contract.getRolloverCapMinutes());

        // Passo 3 — travar os registros do período (RN-121).
        List<PeriodWorkLogEntry> entries = workLogEntries(periodId);
        int lockedWorkLogs =
                workLogSources.stream().mapToInt(source -> source.lockByPeriod(periodId)).sum();

        // Passo 4 — snapshot com checksum. Vem ANTES do passo 5 por INV-PER-08: um período CLOSED
        // sem snapshot é um relatório sem fonte.
        Instant snapshotAt = clock.now();
        List<PeriodAdjustment> adjustments = adjustmentRepository.findByPeriod(periodId);
        PeriodSnapshot snapshot =
                snapshotRepository.save(
                        snapshotBuilder.build(
                                period,
                                balance,
                                carriedOut,
                                toSnapshotWorkLogs(entries),
                                adjustments,
                                parties(contract),
                                snapshotAt));

        // Passo 5 — CLOSED com autor e instante (INV-PER-06).
        period.setCarriedOutMinutes(carriedOut);
        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(snapshotAt);
        period.setClosedBy(tenantContext.currentUserId().orElse(null));

        // Passo 6 — propagar carriedOut como carriedIn do período seguinte (RN-229).
        propagateCarryIn(period, contract, carriedOut);

        // Passo 7 — notificação. Publicada após o commit (BR-128): entrega externa nunca desfaz um
        // fechamento bem-sucedido.
        events.publish(
                new PeriodClosedEvent(
                        periodId, period.getContractId(), carriedOut, snapshot.getChecksum()));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", PeriodStatus.CLOSED.name());
        after.put("carriedOutMinutes", carriedOut);
        after.put("checksum", snapshot.getChecksum());
        auditService.record(
                "PERIOD_CLOSED",
                ENTITY_TYPE,
                periodId,
                Map.of("status", previousStatus.name()),
                after,
                Map.of("lockedWorkLogs", lockedWorkLogs, "confirmed", request.confirmed()));

        log.info(
                "período fechado periodId={} consumedMinutes={} carriedOutMinutes={}"
                        + " lockedWorkLogs={} checksum={}",
                periodId,
                period.getConsumedMinutes(),
                carriedOut,
                lockedWorkLogs,
                snapshot.getChecksum());

        return new ClosePeriodResponse(
                periodId,
                PeriodStatus.CLOSED.name(),
                period.getConsumedMinutes(),
                reconciliationDelta,
                carriedOut,
                lockedWorkLogs,
                snapshot.getChecksum(),
                snapshotAt);
    }

    /**
     * Passo 1: substitui os desnormalizados pela agregação real e registra a diferença.
     *
     * <p>FA-16: o fechamento <b>prossegue</b> com o valor real. Interromper deixaria o período
     * impossível de fechar até uma intervenção manual, quando o valor correto já está disponível. A
     * diferença é auditada e vira alerta: ela significa que um incremento se perdeu, e isso precisa
     * ser investigado — mas depois, e não às custas do fechamento.
     *
     * @return diferença encontrada em {@code consumedMinutes}; zero quando não houve divergência
     */
    private int reconcile(ContractPeriod period) {
        int previousConsumed = period.getConsumedMinutes();
        int realConsumed =
                workLogSources.stream()
                        .mapToInt(source -> source.sumBillableMinutes(period.getId()))
                        .sum();
        int realNonBillable =
                workLogSources.stream()
                        .mapToInt(source -> source.sumNonBillableMinutes(period.getId()))
                        .sum();
        int realAdjustment = adjustmentRepository.sumMinutesByPeriod(period.getId());

        period.setConsumedMinutes(realConsumed);
        period.setNonBillableMinutes(realNonBillable);
        period.setAdjustmentMinutes(realAdjustment);

        int delta = realConsumed - previousConsumed;
        if (delta != 0) {
            auditService.record(
                    "PERIOD_CONSUMPTION_RECONCILED",
                    ENTITY_TYPE,
                    period.getId(),
                    Map.of("consumedMinutes", previousConsumed),
                    Map.of("consumedMinutes", realConsumed),
                    Map.of("deltaMinutes", delta));
            // ERROR com alerta operacional: um desnormalizado divergente significa que o
            // incremento transacional de 008 falhou em algum ponto.
            log.error(
                    "divergência de consumo reconciliada no fechamento periodId={} antes={}"
                            + " depois={} delta={}",
                    period.getId(),
                    previousConsumed,
                    realConsumed,
                    delta);
        }
        return delta;
    }

    /**
     * Passo 6 — RN-229: {@code carriedOut[N]} vira {@code carriedIn[N+1]}; se {@code N+1} não
     * existir, é criado.
     *
     * <p>A criação usa o mesmo {@link PeriodMaterializer} da ativação e da renovação automática, e
     * não um caminho próprio: duas formas de criar período divergiriam na primeira correção feita
     * em apenas uma delas (CA-01). Nasce {@code SCHEDULED} pela mesma razão de {@code
     * GeneratePeriodsJob} — o ciclo seguinte só passa a valer no seu {@code startDate}, e abri-lo
     * antes permitiria lançar horas em um período que ainda não começou.
     *
     * <p><b>Só para contrato {@code ACTIVE}.</b> Um contrato encerrado ou suspenso não ganha ciclo
     * novo: seria um período de apuração sem vigência que o sustente, e no caminho de {@code
     * AutoClosePeriodJob} cada fechamento geraria o período que o próximo fechamento fecharia,
     * indefinidamente. RN-214 é a segunda barreira — o materializador não gera nada além do fim da
     * vigência, e devolver lista vazia é o caso normal do último ciclo.
     *
     * <p>Quando nada é criado, o saldo fica preservado em {@code carriedOutMinutes} deste período e
     * é aplicado se um próximo vier a existir.
     */
    private void propagateCarryIn(ContractPeriod period, Contract contract, int carriedOut) {
        Optional<ContractPeriod> next =
                periodRepository.findByContractIdAndSequence(
                        period.getContractId(), period.getSequence() + 1);
        if (next.isPresent()) {
            next.get().setCarriedInMinutes(carriedOut);
            return;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            log.info(
                    "sem período seguinte para receber carry-over periodId={} carriedOutMinutes={}"
                            + " contractStatus={}",
                    period.getId(),
                    carriedOut,
                    contract.getStatus());
            return;
        }

        List<ContractPeriod> created =
                periodMaterializer.materialize(
                        contract,
                        period.getEndDate(),
                        period.getSequence(),
                        1,
                        PeriodStatus.SCHEDULED);
        if (created.isEmpty()) {
            // RN-214: a vigência terminou neste ciclo. O saldo permanece em carriedOutMinutes.
            log.info(
                    "fim da vigência: carry-over sem destino periodId={} carriedOutMinutes={}",
                    period.getId(),
                    carriedOut);
            return;
        }
        created.get(0).setCarriedInMinutes(carriedOut);
        log.info(
                "período seguinte criado no fechamento para receber carry-over periodId={}"
                        + " nextPeriodId={} carriedOutMinutes={}",
                period.getId(),
                created.get(0).getId(),
                carriedOut);
    }

    private List<PeriodWorkLogEntry> workLogEntries(UUID periodId) {
        return workLogSources.stream()
                .flatMap(source -> source.findByPeriod(periodId).stream())
                .toList();
    }

    /**
     * Linhas do snapshot, com o nome do autor resolvido em lote.
     *
     * <p>O nome é congelado junto com o restante (CX-16 de specs/012): um membro removido do tenant
     * continua nomeado no documento que já foi enviado ao cliente. Resolver por identificador na
     * hora da leitura devolveria "Usuário Removido" em um relatório de dois anos atrás, alterando
     * um documento definitivo — o que RN-701 impede.
     *
     * <p>{@code AuditActorNameResolver} em vez de {@code UserService}: {@code user} já depende de
     * {@code audit}, e a chamada direta fecharia o ciclo que BR-008 proíbe. A porta existe
     * exatamente para isto e já é em lote.
     */
    private List<SnapshotBuilder.SnapshotWorkLog> toSnapshotWorkLogs(
            List<PeriodWorkLogEntry> entries) {
        Map<UUID, String> userNames =
                actorNames.namesOf(entries.stream().map(PeriodWorkLogEntry::userId).toList());
        return entries.stream()
                .map(
                        entry ->
                                new SnapshotBuilder.SnapshotWorkLog(
                                        entry.id().toString(),
                                        entry.workDate().toString(),
                                        entry.startedAt() == null
                                                ? null
                                                : entry.startedAt().toString(),
                                        entry.endedAt() == null ? null : entry.endedAt().toString(),
                                        entry.ticketKey(),
                                        entry.ticketTitle(),
                                        entry.categoryName(),
                                        String.valueOf(entry.userId()),
                                        userNames.get(entry.userId()),
                                        entry.description(),
                                        entry.netMinutes(),
                                        entry.billableMinutes(),
                                        entry.billable(),
                                        entry.tags()))
                .toList();
    }

    /** Cadastro de emissor, cliente e contrato congelado no fechamento (ADR-036 RP-01, RN-703). */
    private SnapshotBuilder.SnapshotParties parties(Contract contract) {
        var issuer = tenantService.issuer();
        var client = clientService.getReportParty(contract.getClientId());
        return new SnapshotBuilder.SnapshotParties(
                issuer.name(),
                issuer.legalName(),
                issuer.documentNumber(),
                issuer.email(),
                issuer.phone(),
                issuer.logoUrl(),
                client.name(),
                client.legalName(),
                client.documentNumber(),
                contract.getCode(),
                contract.getName(),
                contract.getType().name(),
                contract.getMonthlyMinutes(),
                contract.getHourlyRate() == null ? null : contract.getHourlyRate().toPlainString(),
                contract.getOverageRate() == null
                        ? null
                        : contract.getOverageRate().toPlainString(),
                issuer.locale());
    }

    private Contract requireContract(UUID contractId) {
        return contractRepository
                .findById(contractId)
                .orElseThrow(() -> EntityNotFoundException.of(Contract.class, contractId));
    }
}
