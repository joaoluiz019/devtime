package com.devtime.ticket.dto;

import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.ticket.domain.TicketPriority;
import com.devtime.ticket.domain.TicketStatus;
import com.devtime.ticket.domain.TicketType;
import com.devtime.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs de saída da feature 007 (tickets.md §5 a §9; spec §23). */
public final class TicketResponses {

    private TicketResponses() {}

    /** Contrato achatado na resposta do ticket (tickets.md §7). */
    @Schema(name = "TicketContractResponse")
    public record TicketContractResponse(
            UUID id, String code, String name, String status, boolean acceptsWorkLogs) {}

    @Schema(name = "TicketClientResponse")
    public record TicketClientResponse(UUID id, String name, String color) {}

    /**
     * Ticket como {@code 008-worklogs} e {@code 009-timer} precisam dele (AR-02).
     *
     * <p>{@link TicketResponse} expõe {@code TicketType}, {@code TicketStatus} e {@code
     * TicketPriority} — enums do domínio de {@code 007} que as duas features consumidoras não podem
     * conhecer. O que elas realmente usam são os identificadores a copiar para o work log (RN-109),
     * a chave legível para log e mensagem, e a categoria padrão que abre a cadeia de RN-104.
     */
    @Schema(name = "TicketWorkLogRefResponse")
    public record TicketWorkLogRefResponse(
            UUID id,
            String key,
            String title,
            UUID contractId,
            UUID clientId,
            UUID defaultCategoryId) {}

    /**
     * Ticket como {@code 012-reports} precisa dele (AR-02, §7.3 de reports.md).
     *
     * <p>{@link TicketResponse} expõe {@code TicketType}, {@code TicketStatus} e {@code
     * TicketPriority} — enums do domínio de {@code 007} que a feature consumidora não pode conhecer
     * (ART-065). Distinta de {@link TicketDashboardItem} pelo que o cabeçalho de §7.3 exige e o
     * painel não: {@code estimatedMinutes}, para a comparação entre estimativa e realizado, e o
     * identificador do contrato, por onde o relatório chega ao cliente do cabeçalho — o ticket não
     * guarda {@code clientId}, ele o herda do contrato (RN-301).
     */
    @Schema(name = "TicketReportRef")
    public record TicketReportRef(
            UUID id,
            String key,
            String title,
            String status,
            String priority,
            Integer estimatedMinutes,
            int spentMinutes,
            UUID contractId) {}

    /**
     * Ticket aberto do usuário, como {@code 010-dashboard} precisa dele (AR-02).
     *
     * <p>{@link TicketSummaryResponse} expõe {@code TicketType}, {@code TicketStatus} e {@code
     * TicketPriority} — enums do domínio de {@code 007} que o painel não pode conhecer. Aqui os
     * três chegam como texto, que é o que a tela renderiza.
     */
    @Schema(name = "TicketDashboardItem")
    public record TicketDashboardItem(
            UUID id,
            String key,
            String title,
            String status,
            String priority,
            String contractCode,
            LocalDate dueDate,
            int spentMinutes) {}

    /**
     * Detalhe do ticket.
     *
     * @param key chave legível derivada de {@code {contract.code}-{number}}; nunca persistida
     * @param progressRate percentual gasto sobre o estimado; nulo sem estimativa (CX-09)
     * @param isOverEstimate RN-309 — sinaliza, nunca bloqueia; nulo sem estimativa
     * @param availableTransitions ME-06 — filtradas por estado <b>e</b> permissão do requisitante
     */
    @Schema(name = "TicketResponse")
    public record TicketResponse(
            UUID id,
            int number,
            String key,
            String title,
            String description,
            TicketType type,
            TicketStatus status,
            TicketPriority priority,
            TicketContractResponse contract,
            TicketClientResponse client,
            UserSummary assignee,
            UserSummary reporter,
            Integer estimatedMinutes,
            int spentMinutes,
            int billableMinutes,
            BigDecimal progressRate,
            Boolean isOverEstimate,
            String blockReason,
            LocalDate dueDate,
            Instant startedAt,
            Instant completedAt,
            String externalRef,
            UUID defaultCategoryId,
            List<TagOptionResponse> tags,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<TicketStatus> availableTransitions) {}

    /**
     * Item de listagem e cartão do quadro.
     *
     * <p>BR-107 / CP-15: projeção, nunca a entidade. {@code description} está ausente por decisão —
     * carregar 20.000 caracteres em uma listagem de 100 itens trafegaria megabytes inúteis.
     */
    @Schema(name = "TicketSummaryResponse")
    public record TicketSummaryResponse(
            UUID id,
            String key,
            String title,
            TicketType type,
            TicketStatus status,
            TicketPriority priority,
            String contractCode,
            UserSummary assignee,
            Integer estimatedMinutes,
            int spentMinutes,
            BigDecimal progressRate,
            Boolean isOverEstimate,
            List<TagOptionResponse> tags,
            LocalDate dueDate,
            Instant updatedAt) {}

    /** Coluna do quadro (tickets.md §6.1). */
    @Schema(name = "TicketBoardColumn")
    public record TicketBoardColumn(
            TicketStatus status,
            int totalCount,
            int totalSpentMinutes,
            List<TicketSummaryResponse> tickets) {}

    /**
     * Quadro completo.
     *
     * <p>{@code totalCount} reflete o total real da coluna, e não o número de cartões devolvidos: é
     * o que permite à interface indicar que há mais itens do que os exibidos.
     */
    @Schema(name = "TicketBoardResponse")
    public record TicketBoardResponse(List<TicketBoardColumn> columns) {}

    /** Resposta da movimentação de contrato (tickets.md §8.3). */
    @Schema(name = "TicketMoveContractResponse")
    public record TicketMoveContractResponse(
            UUID id, String key, TicketContractResponse contract, String notice) {}

    /**
     * Evento da linha do tempo (tickets.md §9.1).
     *
     * @param type {@code CREATED}, {@code STATUS_CHANGED}, {@code ASSIGNED}, {@code
     *     CONTRACT_MOVED}, {@code UPDATED}, {@code COMMENT} ou {@code WORK_LOG}
     * @param actor autor; nulo em ação de sistema (RN-312)
     */
    @Schema(name = "TicketActivityEvent")
    public record TicketActivityEvent(
            String type, Instant occurredAt, UserSummary actor, Map<String, Object> data) {}

    /**
     * Linha do tempo paginada por cursor.
     *
     * @param cursor instante do evento mais antigo devolvido; alimenta a próxima página
     */
    @Schema(name = "TicketActivityResponse")
    public record TicketActivityResponse(
            List<TicketActivityEvent> content, Instant cursor, boolean hasMore) {}
}
