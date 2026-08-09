package com.devtime.worklog;

import com.devtime.category.CategoryWorkLogSource;
import com.devtime.contract.MemberContractLinkSource;
import com.devtime.contract.PeriodWorkLogSource;
import com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.ticket.TicketWorkLogCountSource;
import com.devtime.ticket.TicketWorkLogCountSource.TicketWorkLogTotals;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribuições de {@code 008-worklogs} às interfaces declaradas por outras features.
 *
 * <p>As três fontes vivem no mesmo arquivo porque compartilham a mesma natureza — adaptar consultas
 * desta feature ao formato que a consumidora declarou — e o mesmo motivo de existir: {@code
 * worklog} depende de {@code ticket} (RN-101) e de {@code contract} (RN-107, RN-231), então nenhuma
 * das duas pode chamá-lo de volta sem fechar o ciclo que AR-09 proíbe. Quem declara a interface é
 * quem precisa do dado; quem a implementa é quem o possui.
 *
 * <p>Nenhuma delas cria aresta nova no grafo de features: as duas dependências já existem.
 */
public final class WorkLogSourceAdapters {

    private WorkLogSourceAdapters() {}

    /** RN-305 / RN-307: contagem real de horas do ticket, para as guardas de {@code 007}. */
    @Component
    @RequiredArgsConstructor
    public static class TicketCountAdapter implements TicketWorkLogCountSource {

        private final WorkLogRepository repository;

        @Override
        @Transactional(readOnly = true)
        public long countByTicket(UUID ticketId) {
            return repository.countByTicketId(ticketId);
        }

        @Override
        @Transactional(readOnly = true)
        public java.util.Map<UUID, TicketWorkLogTotals> totalsByTicket() {
            return repository.totalsByTicket().stream()
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    TicketWorkLogTotal::ticketId,
                                    total ->
                                            new TicketWorkLogTotals(
                                                    (int) total.spentMinutes(),
                                                    (int) total.billableMinutes())));
        }
    }

    /**
     * RN-505: contagem e migração de registros na exclusão de categoria, para {@code 005}.
     *
     * <p>Depende do repositório, e não de {@link WorkLogService}, porque {@code 005} é consumidor
     * desta fonte e {@code worklog} passou a depender de {@code category} em {@code 012} (nome da
     * categoria nas linhas de relatório). Passar pelo service reintroduziria pelo grafo de beans o
     * ciclo que a inversão de dependência existe para evitar — e as duas operações são consultas
     * diretas, sem regra de domínio no caminho.
     */
    @Component
    @RequiredArgsConstructor
    public static class CategoryAdapter implements CategoryWorkLogSource {

        private final WorkLogRepository repository;
        private final TenantContext tenantContext;
        private final TenantClock clock;

        @Override
        @Transactional(readOnly = true)
        public long countByCategory(UUID categoryId) {
            return repository.countByCategoryId(categoryId);
        }

        /**
         * Sem {@code @PreAuthorize}: quem chama é {@code 005}, que já exigiu {@code
         * CATEGORY_MANAGE}. Nenhum minuto muda — só o vínculo de catálogo.
         */
        @Override
        @Transactional
        public long reassignCategory(UUID fromCategoryId, UUID toCategoryId) {
            return repository.reassignCategory(
                    fromCategoryId,
                    toCategoryId,
                    clock.now(),
                    tenantContext.currentUserId().orElse(null));
        }
    }

    /** RN-219, RN-223, RN-241 e RN-243: o que o fechamento de {@code 011} precisa saber. */
    @Component
    @RequiredArgsConstructor
    public static class PeriodAdapter implements PeriodWorkLogSource {

        private final WorkLogService workLogService;

        @Override
        public int sumBillableMinutes(UUID periodId) {
            return workLogService.sumBillableMinutesByPeriod(periodId);
        }

        @Override
        public int sumNonBillableMinutes(UUID periodId) {
            return workLogService.sumNonBillableMinutesByPeriod(periodId);
        }

        @Override
        public int lockByPeriod(UUID periodId) {
            return workLogService.lockByPeriod(periodId);
        }

        @Override
        public int unlockByPeriod(UUID periodId) {
            return workLogService.unlockByPeriod(periodId);
        }

        @Override
        public List<PeriodWorkLogEntry> findByPeriod(UUID periodId) {
            return workLogService.findByPeriodForStatement(periodId);
        }
    }

    /**
     * Metade pendente do escopo de dados de {@code MEMBER} (permissions.md §9).
     *
     * <p>A definição operacional é "contrato C é visível para o membro M se existe work log de M em
     * C <b>ou</b> ticket de M em C". A parte dos tickets chegou com {@code 007}; esta é a dos work
     * logs, que {@code 003} e {@code 004} registraram como pendente até a tabela existir.
     */
    @Component
    @RequiredArgsConstructor
    public static class MemberContractLinkAdapter implements MemberContractLinkSource {

        private final WorkLogRepository repository;

        @Override
        @Transactional(readOnly = true)
        public Set<UUID> contractIdsLinkedTo(UUID userId) {
            if (userId == null) {
                return Set.of();
            }
            return Set.copyOf(repository.findContractIdsByUser(userId));
        }
    }
}
