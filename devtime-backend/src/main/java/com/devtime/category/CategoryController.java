package com.devtime.category;

import com.devtime.category.dto.CategoryRequests.CategoryCreateRequest;
import com.devtime.category.dto.CategoryRequests.CategoryReorderRequest;
import com.devtime.category.dto.CategoryRequests.CategoryUpdateRequest;
import com.devtime.category.dto.CategoryResponses.CategoryDeletionResponse;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de categoria (users.md §8).
 *
 * <p>BR-080: nenhuma regra de negócio aqui — o controller apenas traduz HTTP para o serviço. A
 * permissão é verificada na camada de serviço (BR-161), não apenas aqui.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Catálogo de classificação do trabalho (RN-501 a RN-505)")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(
            summary = "Lista as categorias do tenant",
            description = "Ordenação padrão por sortOrder ascendente (users.md §8.1).")
    @ApiResponse(responseCode = "200", description = "Catálogo do tenant")
    public List<CategoryResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return categoryService.list(active, search);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma categoria")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou de outro tenant")
    })
    public CategoryResponse getById(@PathVariable UUID id) {
        return categoryService.getById(id);
    }

    @PostMapping
    @Operation(summary = "Cria uma categoria", description = "RN-502: nome único por tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Categoria criada"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2601 — nome já existe"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2000 — cor fora do formato hexadecimal")
    })
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse created = categoryService.create(request);
        // BR-088: 201 sempre acompanha Location.
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Atualiza uma categoria",
            description =
                    "RN-504: inativar não afeta registros existentes. Exige version (RN-004).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2601 ou DEVTIME-2004")
    })
    public CategoryResponse update(
            @PathVariable UUID id, @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Exclui uma categoria",
            description =
                    "RN-503: categoria de sistema não é excluível. RN-505: com registros vinculados,"
                            + " replacementCategoryId é obrigatório.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Excluída; informa a contagem migrada"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2602 ou DEVTIME-2603"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2605 — substituta inválida")
    })
    public CategoryDeletionResponse delete(
            @PathVariable UUID id, @RequestParam(required = false) UUID replacementCategoryId) {
        return categoryService.delete(id, replacementCategoryId);
    }

    @PatchMapping("/reorder")
    @Operation(
            summary = "Reordena o catálogo",
            description = "users.md §8.4: a lista deve conter todas as categorias do tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nova ordem aplicada atomicamente"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2000 — lista incompleta ou inválida")
    })
    public List<CategoryResponse> reorder(@Valid @RequestBody CategoryReorderRequest request) {
        return categoryService.reorder(request);
    }
}
