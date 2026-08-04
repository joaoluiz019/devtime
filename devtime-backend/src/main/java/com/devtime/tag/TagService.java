package com.devtime.tag;

import com.devtime.tag.dto.TagRequests.TagCreateRequest;
import com.devtime.tag.dto.TagRequests.TagUpdateRequest;
import com.devtime.tag.dto.TagResponses.TagCleanupSuggestionResponse;
import com.devtime.tag.dto.TagResponses.TagDeleteResponse;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.tag.dto.TagResponses.TagResponse;
import java.util.List;
import java.util.UUID;

/**
 * Interface pública da feature 006 (spec §22.2).
 *
 * <p>{@link #resolveOrCreate(String)} é o contrato consumido por {@code 007-tickets} e,
 * futuramente, por {@code 008-worklogs}: normaliza o nome e devolve a etiqueta existente ou a cria.
 * É idempotente por construção — duas chamadas com entradas que normalizam para o mesmo valor
 * devolvem a mesma etiqueta.
 */
public interface TagService {

    /** users.md §9.1: ordenado por uso decrescente, com filtros opcionais. */
    List<TagResponse> search(String search, Integer minUsage);

    /** Autocompletar com limite de 20 resultados aplicado no servidor (§20 da spec). */
    List<TagOptionResponse> autocomplete(String term);

    TagResponse create(TagCreateRequest request);

    TagResponse update(UUID id, TagUpdateRequest request);

    /** §9.3 de users.md: remove os vínculos e devolve as contagens desvinculadas. */
    TagDeleteResponse delete(UUID id);

    /** RN-508: apenas sugere; nenhuma exclusão automática. */
    TagCleanupSuggestionResponse cleanupSuggestions();

    /**
     * Normaliza e devolve a etiqueta existente ou cria uma nova.
     *
     * <p>Interface pública para {@code 007} e {@code 008} (criação implícita, E-07).
     *
     * @return o identificador da etiqueta resolvida
     */
    UUID resolveOrCreate(String rawName);

    /**
     * Catálogo completo para rotulagem histórica: inclui etiquetas <b>excluídas</b> (spec 006 §22).
     *
     * <p>Interface pública para {@code 012}, pelo mesmo motivo de {@code
     * CategoryService.getAllForReport}: um relatório de período fechado precisa do rótulo vigente à
     * época. Não serve para oferecer opções — para isso existe {@link #autocomplete(String)}.
     */
    List<TagOptionResponse> getAllForReport();
}
