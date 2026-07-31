package com.devtime.ticket;

import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.ticket.domain.Ticket;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.dto.TicketResponses.TicketClientResponse;
import com.devtime.ticket.dto.TicketResponses.TicketContractResponse;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import com.devtime.ticket.dto.TicketResponses.TicketSummaryResponse;
import com.devtime.user.dto.UserSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Ticket} para DTO (ADR-014, BR-104).
 *
 * <p>{@code unmappedTargetPolicy = ERROR}: um campo novo no response sem mapeamento explícito
 * quebra a compilação em vez de chegar nulo à API.
 *
 * <p>BR-105: nenhum acesso a banco e nenhuma regra de negócio. Os dados de outras features
 * (contrato, cliente, pessoas, etiquetas) chegam <b>já resolvidos</b> pelo serviço, em consultas em
 * lote — resolvê-los aqui produziria N+1 na listagem e no quadro.
 *
 * <p>BR-106: a derivação de {@code progressRate} e de {@code isOverEstimate} é apresentação, não
 * domínio, e por isso mora aqui. O arredondamento é {@code HALF_UP} com 2 casas (ART-042).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TicketMapper {

    // A origem de cada campo é declarada explicitamente: com cinco parâmetros de entrada, `id` e
    // outros nomes existem em mais de um deles, e MapStruct recusa a ambiguidade em vez de
    // escolher — que é exatamente o comportamento desejado (BR-104).
    @Mapping(target = "id", source = "ticket.id")
    @Mapping(target = "key", source = "key")
    @Mapping(target = "title", source = "ticket.title")
    @Mapping(target = "type", source = "ticket.type")
    @Mapping(target = "status", source = "ticket.status")
    @Mapping(target = "priority", source = "ticket.priority")
    @Mapping(target = "contractCode", source = "contractCode")
    @Mapping(target = "assignee", source = "assignee")
    @Mapping(target = "estimatedMinutes", source = "ticket.estimatedMinutes")
    @Mapping(target = "spentMinutes", source = "ticket.spentMinutes")
    @Mapping(target = "dueDate", source = "ticket.dueDate")
    @Mapping(target = "updatedAt", source = "ticket.updatedAt")
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "progressRate", expression = "java(progressRate(ticket))")
    @Mapping(target = "isOverEstimate", expression = "java(ticket.isOverEstimate())")
    TicketSummaryResponse toSummary(
            Ticket ticket,
            String key,
            String contractCode,
            UserSummary assignee,
            List<TagOptionResponse> tags);

    /**
     * Detalhe completo.
     *
     * <p>Escrito como método {@code default} porque nenhum de seus oito parâmetros é derivável da
     * entidade: contrato, cliente e pessoas pertencem a outras features e chegam como DTO público,
     * e {@code availableTransitions} depende do papel do requisitante (ME-06). Um mapeamento gerado
     * apenas repetiria a mesma atribuição campo a campo.
     */
    default TicketResponse toResponse(
            Ticket ticket,
            String key,
            TicketContractResponse contract,
            TicketClientResponse client,
            UserSummary assignee,
            UserSummary reporter,
            List<TagOptionResponse> tags,
            List<TicketStatus> availableTransitions) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getNumber(),
                key,
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getType(),
                ticket.getStatus(),
                ticket.getPriority(),
                contract,
                client,
                assignee,
                reporter,
                ticket.getEstimatedMinutes(),
                ticket.getSpentMinutes(),
                ticket.getBillableMinutes(),
                progressRate(ticket),
                ticket.isOverEstimate(),
                ticket.getBlockReason(),
                ticket.getDueDate(),
                ticket.getStartedAt(),
                ticket.getCompletedAt(),
                ticket.getExternalRef(),
                ticket.getDefaultCategoryId(),
                tags,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getVersion() == null ? 0L : ticket.getVersion(),
                availableTransitions);
    }

    /**
     * {@code spentMinutes / estimatedMinutes × 100}.
     *
     * <p>Nulo sem estimativa: sem referência não existe progresso a exibir, e devolver {@code 0}
     * sugeriria "nada feito" em um ticket que pode ter dezenas de horas (CX-09). Estimativa zero
     * também devolve nulo — a divisão não é definida e o ticket não tem meta a comparar.
     */
    default BigDecimal progressRate(Ticket ticket) {
        Integer estimated = ticket.getEstimatedMinutes();
        if (estimated == null || estimated == 0) {
            return null;
        }
        return BigDecimal.valueOf(ticket.getSpentMinutes())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(estimated), 2, RoundingMode.HALF_UP);
    }
}
