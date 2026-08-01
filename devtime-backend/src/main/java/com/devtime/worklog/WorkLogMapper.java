package com.devtime.worklog;

import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.user.dto.UserSummary;
import com.devtime.worklog.domain.WorkLog;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogCategoryResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogConflictResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogSummaryResponse;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogTicketResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link WorkLog} para DTO (BR-104 a BR-106).
 *
 * <p>Escrito à mão em vez de gerado por MapStruct: todos os métodos precisariam de {@code @Mapping}
 * explícito para cada campo — ticket, categoria, usuário e etiquetas chegam <b>já resolvidos</b>
 * pelo serviço, em consultas em lote — e o resultado gerado seria idêntico à atribuição campo a
 * campo, com uma camada a mais para depurar. {@code TicketMapper} adota o mesmo caminho em {@code
 * toResponse}, pela mesma razão.
 *
 * <p>BR-105: nenhum acesso a banco, nenhuma regra de negócio. BR-106: a formatação de duração é
 * apresentação e mora aqui, nunca na entidade.
 */
@Component
public class WorkLogMapper {

    public WorkLogResponse toResponse(
            WorkLog workLog,
            WorkLogTicketResponse ticket,
            WorkLogCategoryResponse category,
            UserSummary user,
            List<TagOptionResponse> tags) {
        return new WorkLogResponse(
                workLog.getId(),
                ticket,
                workLog.getContractId(),
                workLog.getClientId(),
                workLog.getContractPeriodId(),
                user,
                category,
                workLog.getWorkDate(),
                workLog.getStartedAt(),
                workLog.getEndedAt(),
                workLog.getGrossMinutes(),
                workLog.getPausedMinutes(),
                workLog.getNetMinutes(),
                workLog.billableMinutes(),
                durationLabel(workLog.getNetMinutes()),
                workLog.getDescription(),
                workLog.isBillable(),
                workLog.getSource().name(),
                workLog.getTimerId(),
                workLog.getLockedAt(),
                workLog.getEditCount(),
                tags,
                workLog.getCreatedAt(),
                workLog.getUpdatedAt(),
                workLog.getVersion() == null ? 0L : workLog.getVersion());
    }

    /** Listagem: sem {@code description} (BR-107, CP-15) e sem etiquetas resolvidas por linha. */
    public WorkLogSummaryResponse toSummary(
            WorkLog workLog, String ticketKey, String categoryName) {
        return new WorkLogSummaryResponse(
                workLog.getId(),
                workLog.getWorkDate(),
                workLog.getStartedAt(),
                workLog.getEndedAt(),
                ticketKey,
                workLog.getTicketId(),
                categoryName,
                workLog.getUserId(),
                workLog.getNetMinutes(),
                workLog.billableMinutes(),
                durationLabel(workLog.getNetMinutes()),
                workLog.isBillable(),
                workLog.getSource().name(),
                workLog.getLockedAt());
    }

    /** §19.1: o conflito viaja <b>sem</b> descrição — pode ser de outro membro. */
    public WorkLogConflictResponse toConflict(WorkLog workLog, String ticketKey) {
        return new WorkLogConflictResponse(
                workLog.getId(),
                workLog.getWorkDate(),
                workLog.getStartedAt(),
                workLog.getEndedAt(),
                ticketKey);
    }

    /**
     * Minutos em {@code HH:MM}.
     *
     * <p>As horas não são limitadas a 24: um total agregado de 150 horas precisa aparecer como
     * {@code 150:00}, não como {@code 06:00} de um sexto dia.
     */
    public String durationLabel(int minutes) {
        int absolute = Math.abs(minutes);
        String sign = minutes < 0 ? "-" : "";
        return String.format("%s%02d:%02d", sign, absolute / 60, absolute % 60);
    }
}
