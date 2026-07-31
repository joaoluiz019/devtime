package com.devtime.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 006 (users.md §9, spec §23). */
public final class TagResponses {

    private TagResponses() {}

    /**
     * Etiqueta completa.
     *
     * @param name sempre o nome <b>normalizado</b> (§9.2 de users.md) — é o que o usuário precisa
     *     ver imediatamente após criar, sob pena de a normalização parecer um defeito
     */
    @Schema(name = "TagResponse")
    public record TagResponse(UUID id, String name, String color, int usageCount, long version) {}

    /** Projeção enxuta do autocompletar: sem {@code usageCount}, sem {@code version}. */
    @Schema(name = "TagOptionResponse")
    public record TagOptionResponse(UUID id, String name, String color) {}

    /**
     * users.md §9.3: a exclusão responde {@code 200} com as contagens, não {@code 204}.
     *
     * <p>O usuário precisa saber que milhares de registros perderam um rótulo.
     *
     * @param unlinkedFromWorkLogs sempre {@code 0} nesta sprint — {@code work_log_tags} chega com
     *     {@code 008-worklogs}
     */
    @Schema(name = "TagDeleteResponse")
    public record TagDeleteResponse(long unlinkedFromTickets, long unlinkedFromWorkLogs) {}

    /** RN-508: apenas sugestão; nenhuma exclusão automática. */
    @Schema(name = "TagCleanupSuggestionResponse")
    public record TagCleanupSuggestionResponse(List<TagCleanupSuggestion> tags, int orphanDays) {}

    /**
     * @param orphanSince instante da última alteração, base do cálculo dos 90 dias
     */
    @Schema(name = "TagCleanupSuggestion")
    public record TagCleanupSuggestion(UUID id, String name, String color, Instant orphanSince) {}
}
