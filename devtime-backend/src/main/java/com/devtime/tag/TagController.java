package com.devtime.tag;

import com.devtime.tag.dto.TagRequests.TagCreateRequest;
import com.devtime.tag.dto.TagRequests.TagUpdateRequest;
import com.devtime.tag.dto.TagResponses.TagCleanupSuggestionResponse;
import com.devtime.tag.dto.TagResponses.TagDeleteResponse;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.tag.dto.TagResponses.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de etiqueta (users.md §9).
 *
 * <p>BR-080: nenhuma regra de negócio aqui. A permissão é verificada na camada de serviço (BR-161).
 */
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Etiquetas", description = "Vocabulário livre de rótulos do tenant (RN-506 a RN-508)")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(
            summary = "Lista as etiquetas do tenant",
            description =
                    "Ordenação por usageCount decrescente (users.md §9.1). O termo de busca passa"
                            + " pela mesma normalização do nome (RN-506).")
    @ApiResponse(responseCode = "200", description = "Etiquetas do tenant")
    public List<TagResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @PositiveOrZero Integer minUsage) {
        return tagService.search(search, minUsage);
    }

    @GetMapping("/autocomplete")
    @Operation(
            summary = "Sugere etiquetas existentes",
            description =
                    "Limite de 20 resultados aplicado no servidor. Existe para que o usuário"
                            + " reaproveite o vocabulário em vez de criar quase-duplicatas.")
    @ApiResponse(responseCode = "200", description = "Até 20 sugestões")
    public List<TagOptionResponse> autocomplete(@RequestParam(required = false) String term) {
        return tagService.autocomplete(term);
    }

    @GetMapping("/cleanup-suggestions")
    @Operation(
            summary = "Etiquetas sem uso há mais de 90 dias",
            description = "RN-508: apenas sugere. Nenhuma exclusão é automática.")
    @ApiResponse(responseCode = "200", description = "Sugestões de limpeza")
    public TagCleanupSuggestionResponse cleanupSuggestions() {
        return tagService.cleanupSuggestions();
    }

    @PostMapping
    @Operation(
            summary = "Cria uma etiqueta",
            description =
                    "RN-506: o nome é normalizado antes de qualquer validação. A resposta traz o"
                            + " nome já normalizado.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Etiqueta criada"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2604 — nome normalizado já existe"),
        @ApiResponse(
                responseCode = "422",
                description = "DEVTIME-2000 — nome fora de 2–40 caracteres após a normalização")
    })
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        TagResponse created = tagService.create(request);
        // BR-088: 201 sempre acompanha Location.
        return ResponseEntity.created(URI.create("/api/v1/tags/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Renomeia ou altera a cor",
            description =
                    "Vínculos e usageCount são preservados na renomeação. Exige version (RN-004).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Etiqueta atualizada"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2604 ou DEVTIME-2004"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou de outro tenant")
    })
    public TagResponse update(@PathVariable UUID id, @Valid @RequestBody TagUpdateRequest request) {
        return tagService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Exclui uma etiqueta",
            description =
                    "§9.3 de users.md: remove todos os vínculos e responde 200 com as contagens"
                            + " desvinculadas — o usuário precisa saber quantos registros perderam"
                            + " o rótulo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Excluída; informa as contagens"),
        @ApiResponse(
                responseCode = "404",
                description = "DEVTIME-2002 — inexistente ou de outro tenant")
    })
    public TagDeleteResponse delete(@PathVariable UUID id) {
        return tagService.delete(id);
    }
}
