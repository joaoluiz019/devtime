package com.devtime.timer;

import com.devtime.category.CategoryService;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.ticket.TicketService;
import com.devtime.timer.domain.Timer;
import com.devtime.timer.dto.TimerResponses.ActiveTimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerCategoryResponse;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerTicketResponse;
import com.devtime.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de cronômetro (ver {@link TimerQueryService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimerQueryServiceImpl implements TimerQueryService {

    private final TimerRepository repository;
    private final TimerMapper mapper;
    private final TicketService ticketService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final TenantContext tenantContext;
    private final com.devtime.shared.time.TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public Optional<TimerResponse> current() {
        // OWN-05: o cronômetro é resolvido pelo usuário do token, nunca por identificador da
        // requisição — não existe caminho para consultar o cronômetro de terceiro (SG-01).
        return repository.findActiveByUser(tenantContext.requireUserId()).map(this::toResponse);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TIMER_VIEW_ANY')")
    public List<ActiveTimerResponse> activeInTenant() {
        return repository.findActiveInTenant().stream()
                .map(
                        timer ->
                                mapper.toActive(
                                        timer,
                                        userService.summaryOf(timer.getUserId()).name(),
                                        ticketService.getKeyById(timer.getTicketId())))
                .toList();
    }

    /**
     * Sem {@code @PreAuthorize}: consumido por {@code 007} de dentro da transação de transição do
     * ticket, que já verificou {@code TICKET_TRANSITION}. Exigir {@code TIMER_VIEW_ANY} aqui
     * impediria um {@code MEMBER} de concluir o próprio ticket.
     */
    @Override
    public boolean hasActiveForTicket(UUID ticketId) {
        return repository.existsActiveForTicket(ticketId);
    }

    @Override
    public List<UUID> activeTimerIdsForTicket(UUID ticketId) {
        return repository.findActiveIdsForTicket(ticketId);
    }

    /**
     * Sem {@code @PreAuthorize} pelo mesmo motivo: {@code 011} já verificou {@code PERIOD_CLOSE}.
     */
    @Override
    public List<UUID> activeTimerIdsForTickets(List<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return List.of();
        }
        return repository.findActiveIdsForTickets(ticketIds);
    }

    /** {@code quickStats.activeTimerMinutes} (ver {@link TimerQueryService}). */
    @Override
    @PreAuthorize("hasPermission(null, 'TIMER_USE')")
    public int activeMinutesInCurrentTenant() {
        return repository
                .findActiveByUser(tenantContext.requireUserId())
                // CX-11: findActiveByUser é @CrossTenant por exigência de RN-150. O painel descreve
                // o tenant corrente, então o cronômetro de outra organização é descartado aqui.
                .filter(timer -> timer.getTenantId().equals(tenantContext.requireTenantId()))
                // ART-036: segundos truncados, nunca arredondados.
                .map(timer -> timer.elapsedSeconds(clock.now()) / 60)
                .orElse(0);
    }

    TimerResponse toResponse(Timer timer) {
        // CX-04 / CX-22: exibição usa getRef, que NÃO aplica RN-306. Um cronômetro cujo contrato
        // encerrou durante a execução precisa continuar visível; é o encerramento dele que falha.
        var ticket = ticketService.getRef(timer.getTicketId());
        CategoryResponse category = categoryService.getById(timer.getCategoryId());
        return mapper.toResponse(
                timer,
                new TimerTicketResponse(ticket.id(), ticket.key(), ticket.title()),
                new TimerCategoryResponse(category.id(), category.name(), category.color()));
    }
}
