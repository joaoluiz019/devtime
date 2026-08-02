package com.devtime.contract;

import com.devtime.contract.dto.ContractResponses.MaintenanceTarget;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.shared.time.TenantClock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Jobs de ciclo de vida de contratos e períodos (spec 004 §22.4, T-004-29 e T-004-30).
 *
 * <p>São as três transições que o tempo dispara sozinho. Sem elas, um contrato ativo simplesmente
 * para de funcionar ao fim do ciclo: não há próximo período, e todo registro de horas passa a
 * falhar com {@code DEVTIME-2107}. É por isso que a geração roda antes (03:00) da abertura (00:05
 * do dia seguinte) — o período precisa existir na véspera para poder abrir na virada.
 *
 * <p><b>JB-04 / CP-12: a falha em um tenant não interrompe os demais.</b> Cada alvo roda em
 * transação e contexto próprios, e a exceção é registrada e engolida. Um contrato com dado
 * inconsistente não pode impedir a renovação de todos os outros do sistema.
 *
 * <p>BR-184: {@code @SchedulerLock} em todos — duas réplicas gerando o mesmo período produziriam
 * violação do índice único {@code (contract_id, sequence)} (R-04). BR-186: lote com teto por
 * execução.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class ContractSchedulingJobs {

    /** RN-213: o período seguinte é criado quando faltam três dias ou menos para o fim. */
    static final int RENEWAL_DAYS_AHEAD = 3;

    /** BR-186: teto por execução. §20 exige menos de 5 minutos para 10.000 contratos. */
    static final int BATCH_SIZE = 500;

    private final ContractMaintenanceService maintenanceService;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    /**
     * RN-213: cria o próximo período dos contratos que renovam.
     *
     * <p>Às 03:00, e não à meia-noite: a janela de três dias torna o horário exato irrelevante para
     * a regra, e um horário de baixa atividade evita disputar lock com o uso real do sistema.
     *
     * <p>RS-06 registra a intenção de rodar às 03:00 <b>no fuso do tenant</b>. O agendamento é
     * único e roda no fuso do servidor: a janela de três dias absorve a diferença — o período é
     * criado com até 72 horas de antecedência, e nenhum fuso do mundo desloca isso o suficiente
     * para que o ciclo vire sem sucessor. Um agendamento por fuso multiplicaria execuções e locks
     * para resolver um problema que a folga já resolve.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "generatePeriods", lockAtMostFor = "PT30M")
    public void generateUpcomingPeriods() {
        LocalDate today = clock.today();
        List<MaintenanceTarget> due =
                maintenanceService.findRenewalDue(today, RENEWAL_DAYS_AHEAD, BATCH_SIZE);

        int created = 0;
        for (MaintenanceTarget target : due) {
            created +=
                    inTenant(
                                    target.tenantId(),
                                    () -> maintenanceService.renewPeriod(target.entityId()))
                            ? 1
                            : 0;
        }
        log.info("geração de períodos concluída candidatos={} criados={}", due.size(), created);
    }

    /** §22.4: {@code SCHEDULED → OPEN} no {@code startDate}. */
    @Scheduled(cron = "0 5 0 * * *")
    @SchedulerLock(name = "openPeriods", lockAtMostFor = "PT10M")
    public void openScheduledPeriods() {
        List<MaintenanceTarget> due =
                maintenanceService.findScheduledDue(clock.today(), BATCH_SIZE);

        int opened = 0;
        for (MaintenanceTarget target : due) {
            opened +=
                    inTenant(
                                    target.tenantId(),
                                    () -> maintenanceService.openScheduledPeriod(target.entityId()))
                            ? 1
                            : 0;
        }
        log.info("abertura de períodos concluída candidatos={} abertos={}", due.size(), opened);
    }

    /**
     * FA-05: {@code ACTIVE → ENDED} ao atingir a data de fim.
     *
     * <p>Roda depois da abertura de períodos, e não antes: um contrato que termina hoje não deve
     * receber a abertura de um período que já não vale, e a ordem inversa criaria e abriria um
     * ciclo para encerrá-lo em seguida.
     */
    @Scheduled(cron = "0 10 0 * * *")
    @SchedulerLock(name = "autoEndContracts", lockAtMostFor = "PT15M")
    public void endExpiredContracts() {
        List<MaintenanceTarget> due = maintenanceService.findEndDue(clock.today(), BATCH_SIZE);

        int ended = 0;
        for (MaintenanceTarget target : due) {
            ended +=
                    inTenant(
                                    target.tenantId(),
                                    () -> maintenanceService.endContract(target.contractId()))
                            ? 1
                            : 0;
        }
        if (ended > 0) {
            log.warn("contratos encerrados automaticamente quantidade={}", ended);
        }
        log.info(
                "encerramento automático concluído candidatos={} encerrados={}", due.size(), ended);
    }

    /**
     * Executa no contexto do tenant informado (BR-049, JB-06).
     *
     * <p>A sessão é sintética e sem usuário: {@code actorId} nulo faz a trilha registrar {@code
     * actorType = SYSTEM} (CE-S-06), que é a verdade — nenhuma pessoa pediu esta transição. O papel
     * {@code OWNER} existe apenas para satisfazer a verificação de permissão do serviço de
     * contrato, que é o mesmo caminho do encerramento manual.
     *
     * <p>CX-11: a falha é registrada e não propaga. O tenant seguinte continua.
     */
    private boolean inTenant(UUID tenantId, BooleanSupplier action) {
        var previous = tenantContext.session().orElse(null);
        tenantContext.set(
                TenantSession.system(tenantId, Role.OWNER, RolePermissions.of(Role.OWNER)));
        try {
            return action.getAsBoolean();
        } catch (RuntimeException failure) {
            log.error("falha em job de contrato tenantId={}", tenantId, failure);
            return false;
        } finally {
            if (previous == null) {
                tenantContext.clear();
            } else {
                tenantContext.set(previous);
            }
        }
    }
}
