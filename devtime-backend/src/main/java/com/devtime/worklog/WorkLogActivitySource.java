package com.devtime.worklog;

import com.devtime.ticket.TicketActivitySource;
import com.devtime.ticket.dto.TicketResponses.TicketActivityEvent;
import com.devtime.user.UserService;
import com.devtime.worklog.domain.WorkLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work logs na linha do tempo do ticket (tickets.md §9.1).
 *
 * <p>Fecha a pendência registrada em {@code TicketActivityServiceImpl}: {@code 007} declarou {@link
 * TicketActivitySource} justamente para receber esta contribuição quando {@code 008} existisse.
 * Nenhuma aresta nova no grafo — {@code worklog} já depende de {@code ticket} (RN-101).
 *
 * <p><b>O escopo de dados de {@code MEMBER} é aplicado na consulta</b> (§9 de permissions.md,
 * IMP-02), não sobre a lista carregada: quem não tem {@code WORKLOG_VIEW_ANY} vê apenas os próprios
 * registros, e vê-los desaparecer <b>depois</b> de contados vazaria pela paginação exatamente a
 * informação que a restrição existe para proteger.
 */
@Component
@RequiredArgsConstructor
public class WorkLogActivitySource implements TicketActivitySource {

    private final WorkLogRepository repository;
    private final WorkLogOwnershipPolicy ownershipPolicy;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public List<TicketActivityEvent> activityOf(UUID ticketId) {
        return repository
                .findForTicketActivity(ticketId, ownershipPolicy.dataScopeUserId().orElse(null))
                .stream()
                .map(this::toEvent)
                .toList();
    }

    /**
     * O instante do evento é {@code startedAt}, não {@code createdAt}.
     *
     * <p>A linha do tempo conta a história do trabalho, e o trabalho aconteceu quando aconteceu —
     * um lançamento retroativo de ontem pertence a ontem, e ordená-lo pelo momento da digitação o
     * colocaria fora de ordem em relação aos comentários daquele dia.
     */
    private TicketActivityEvent toEvent(WorkLog workLog) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workLogId", workLog.getId());
        data.put("workDate", workLog.getWorkDate());
        data.put("netMinutes", workLog.getNetMinutes());
        data.put("billable", workLog.isBillable());
        data.put("description", workLog.getDescription());
        return new TicketActivityEvent(
                "WORKLOG_ADDED",
                workLog.getStartedAt(),
                workLog.getUserId() == null ? null : userService.summaryOf(workLog.getUserId()),
                data);
    }
}
