package com.devtime.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** DTOs de saída da feature 005 (users.md §8.1 e §8.3). */
public final class CategoryResponses {

    private CategoryResponses() {}

    /**
     * Representação de uma categoria.
     *
     * <p>O bloco {@code usage} de users.md §8.1 ({@code workLogsCount}, {@code totalMinutes}) não é
     * emitido nesta sprint: depende de {@code work_logs}, tabela introduzida por {@code
     * 008-worklogs}. Omitir é preferível a emitir zeros, que seriam lidos como "nenhuma hora
     * registrada" em vez de "informação indisponível".
     */
    @Schema(name = "CategoryResponse")
    public record CategoryResponse(
            UUID id,
            String name,
            String description,
            String color,
            String icon,
            boolean billableByDefault,
            boolean active,
            int sortOrder,
            boolean isSystem,
            long version) {}

    /**
     * Resultado da exclusão (users.md §8.3).
     *
     * @param migratedWorkLogs registros migrados para a substituta (RN-505)
     * @param migratedTo categoria substituta, nula quando não houve migração
     */
    @Schema(name = "CategoryDeletionResponse")
    public record CategoryDeletionResponse(long migratedWorkLogs, UUID migratedTo) {}
}
