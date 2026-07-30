package com.devtime.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada da feature 005 (users.md §8, spec §23).
 *
 * <p>BR-100/BR-101: todo DTO é {@code record} imutável e request nunca é reutilizado como response.
 * {@code isSystem} está ausente de todos eles — INV-CAT-05 o torna imutável, e um campo ausente do
 * contrato é mais forte que um campo validado (SG de spec §19).
 */
public final class CategoryRequests {

    /** Formato hexadecimal exigido por users.md §8.2; violação resulta em {@code DEVTIME-2000}. */
    public static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";

    private CategoryRequests() {}

    /** users.md §8.2. */
    @Schema(name = "CategoryCreateRequest")
    public record CategoryCreateRequest(
            @NotBlank @Size(min = 2, max = 60) String name,
            @Size(max = 255) String description,
            @Pattern(regexp = HEX_COLOR) String color,
            @Size(max = 40) String icon,
            Boolean billableByDefault,
            @PositiveOrZero Integer sortOrder) {}

    /**
     * Atualização de categoria.
     *
     * <p>{@code version} é obrigatório (RN-004): sem ele, uma edição concorrente sobrescreveria a
     * outra silenciosamente.
     */
    @Schema(name = "CategoryUpdateRequest")
    public record CategoryUpdateRequest(
            @NotBlank @Size(min = 2, max = 60) String name,
            @Size(max = 255) String description,
            @Pattern(regexp = HEX_COLOR) String color,
            @Size(max = 40) String icon,
            @NotNull Boolean billableByDefault,
            @NotNull Boolean active,
            @PositiveOrZero Integer sortOrder,
            @NotNull Long version) {}

    /** users.md §8.4: a lista deve conter <b>todas</b> as categorias do tenant. */
    @Schema(name = "CategoryReorderRequest")
    public record CategoryReorderRequest(@NotEmpty List<@NotNull UUID> orderedIds) {}
}
