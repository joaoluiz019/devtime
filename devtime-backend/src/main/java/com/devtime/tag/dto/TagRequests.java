package com.devtime.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada da feature 006 (users.md §9, spec §23).
 *
 * <p>{@code usageCount} está ausente de todos eles: é desnormalizado e só muda por efeito de
 * vínculo ou reconciliação (SG-05 da spec). Campo ausente do contrato é barreira mais forte que
 * campo validado.
 */
public final class TagRequests {

    /** Formato hexadecimal de users.md §9.2; violação resulta em {@code DEVTIME-2000}. */
    public static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";

    /**
     * Limite do nome <b>antes</b> da normalização.
     *
     * <p>Proteção contra payload abusivo, não a regra de negócio: RN-507 limita o nome
     * <b>normalizado</b> a 40, e uma entrada com muitos espaços encolhe (CX-05).
     */
    public static final int MAX_RAW_NAME_LENGTH = 60;

    private TagRequests() {}

    /** users.md §9.2. */
    @Schema(name = "TagCreateRequest")
    public record TagCreateRequest(
            @NotBlank @Size(max = MAX_RAW_NAME_LENGTH) String name,
            @Pattern(regexp = HEX_COLOR) String color) {}

    /**
     * Renomeação e alteração de cor.
     *
     * <p>Campos nulos preservam o valor atual — a rota é {@code PATCH} (users.md §9). {@code
     * version} é obrigatório (RN-004).
     */
    @Schema(name = "TagUpdateRequest")
    public record TagUpdateRequest(
            @Size(max = MAX_RAW_NAME_LENGTH) String name,
            @Pattern(regexp = HEX_COLOR) String color,
            @NotNull Long version) {}

    /**
     * Vínculo de etiquetas a um alvo.
     *
     * <p>{@code tagNames} aciona {@code resolveOrCreate} (criação implícita, E-07 da spec); {@code
     * tagIds} referencia etiquetas já existentes. Os dois podem coexistir e o resultado é a união.
     */
    @Schema(name = "TagLinkRequest")
    public record TagLinkRequest(List<UUID> tagIds, List<@NotBlank String> tagNames) {}
}
