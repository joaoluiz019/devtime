package com.devtime.category;

import com.devtime.category.domain.Category;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Category} para DTO (ADR-014, BR-104).
 *
 * <p>{@code unmappedTargetPolicy = ERROR}: um campo novo no response sem mapeamento explícito
 * quebra a compilação em vez de chegar nulo à API — o modo de falha que MapStruct existe para
 * eliminar.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CategoryMapper {

    @Mapping(target = "isSystem", source = "system")
    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponses(List<Category> categories);
}
