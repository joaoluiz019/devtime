package com.devtime.tag;

import com.devtime.tag.domain.Tag;
import com.devtime.tag.dto.TagResponses.TagCleanupSuggestion;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.tag.dto.TagResponses.TagResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Tag} para DTO (ADR-014, BR-104).
 *
 * <p>Nenhum mapeamento não trivial: o nome já está normalizado na persistência (INV-TAG-03), então
 * não há transformação a fazer na leitura.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TagMapper {

    TagResponse toResponse(Tag tag);

    List<TagResponse> toResponses(List<Tag> tags);

    TagOptionResponse toOption(Tag tag);

    List<TagOptionResponse> toOptions(List<Tag> tags);

    /**
     * {@code orphanSince} é {@code updatedAt}: o instante a partir do qual a etiqueta está ociosa.
     */
    @Mapping(target = "orphanSince", source = "updatedAt")
    TagCleanupSuggestion toSuggestion(Tag tag);

    List<TagCleanupSuggestion> toSuggestions(List<Tag> tags);
}
