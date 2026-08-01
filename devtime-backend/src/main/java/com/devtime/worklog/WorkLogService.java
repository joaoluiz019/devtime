package com.devtime.worklog;

import com.devtime.shared.pagination.PageResponse;
import com.devtime.worklog.dto.WorkLogFilter;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogDuplicateRequest;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogUpdateRequest;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCreatedResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogSummaryResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Interface pública da feature 008 (spec §22.2).
 *
 * <p>É o ponto único de entrada de horas no sistema. {@code 009-timer}, e no futuro a importação em
 * massa e a sugestão automática, <b>delegam a {@link #createFromTimer}</b>, que por sua vez delega
 * ao mesmo {@code create} — nunca a um caminho paralelo de validação (RN-159, CP-14). Dois
 * conjuntos de validação divergiriam na primeira alteração de regra, e é exatamente esse modo de
 * falha que RN-159 existe para impedir.
 */
public interface WorkLogService {

    /** worklogs.md §5: listagem paginada com filtros compostos. Devolve projeção (BR-107). */
    PageResponse<WorkLogSummaryResponse> search(WorkLogFilter filter, Pageable pageable);

    WorkLogResponse getById(UUID id);

    /**
     * Criação manual, na ordem <b>normativa</b> da §6.1 da spec (SV-03, BR-062).
     *
     * <p>A ordem determina qual erro o usuário vê quando o payload viola várias regras ao mesmo
     * tempo, e por isso é testada explicitamente (CA-09, OB-04).
     */
    WorkLogCreatedResponse create(WorkLogCreateRequest request);

    /** FA-09: revalida a §6.1 integralmente; incrementa {@code editCount} (RN-123). */
    WorkLogCreatedResponse update(UUID id, WorkLogUpdateRequest request);

    /** RN-125: exclusão lógica; devolve saldo ao período e reduz {@code ticket.spentMinutes}. */
    void delete(UUID id);

    /** FA-14: copia o registro exigindo <b>novo horário</b> — o mesmo criaria sobreposição. */
    WorkLogCreatedResponse duplicate(UUID id, WorkLogDuplicateRequest request);

    /**
     * RN-159: gera o work log do cronômetro reusando integralmente {@link
     * #create(WorkLogCreateRequest)}.
     *
     * <p>Interface pública para {@code 009-timer}. RN-160: quando qualquer validação falha, a
     * exceção propaga e o chamador <b>preserva o cronômetro</b> — nada aqui altera o timer.
     *
     * @param timerId preenche {@code timerId} e força {@code source = TIMER} (INV-WKL-09)
     */
    WorkLogCreatedResponse createFromTimer(
            UUID timerId,
            UUID ticketId,
            UUID categoryId,
            UUID userId,
            Instant startedAt,
            Instant endedAt,
            int pausedMinutes,
            String description,
            boolean billable);

    /** RN-305 / RN-307: existência real de horas no ticket, publicada a {@code 007}. */
    long countByTicket(UUID ticketId);

    /** RN-505: registros vinculados a uma categoria, publicado a {@code 005}. */
    long countByCategory(UUID categoryId);

    /** RN-219: soma canônica de {@code billableMinutes} do período, publicada a {@code 011}. */
    int sumBillableMinutesByPeriod(UUID periodId);

    /** RN-223: soma de minutos não faturáveis do período, publicada a {@code 011}. */
    int sumNonBillableMinutesByPeriod(UUID periodId);

    /**
     * RN-241 passo 3: trava todos os registros do período no fechamento.
     *
     * @return quantidade travada, exibida no resumo do fechamento
     */
    int lockByPeriod(UUID periodId);

    /** RN-243: a reabertura limpa {@code lockedAt} dos registros do período. */
    int unlockByPeriod(UUID periodId);

    /**
     * Registros do período, resolvidos para exibição, publicados a {@code 011-bank-hours}.
     *
     * <p>Alimenta o extrato explicativo e o payload do snapshot (RN-241 passo 4). {@code ticketKey}
     * e {@code categoryName} chegam resolvidos porque o snapshot é imutável e precisa continuar
     * legível anos depois, mesmo que o ticket mude de nome ou a categoria seja excluída.
     */
    java.util.List<com.devtime.contract.dto.BalanceResponses.PeriodWorkLogEntry>
            findByPeriodForStatement(UUID periodId);
}
