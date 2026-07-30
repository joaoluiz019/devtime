package com.devtime.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.category.dto.CategoryRequests.CategoryCreateRequest;
import com.devtime.category.dto.CategoryRequests.CategoryReorderRequest;
import com.devtime.category.dto.CategoryRequests.CategoryUpdateRequest;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Regras do catálogo de categorias (RN-501 a RN-505, spec 005). */
class CategoryServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private CategoryService categoryService;

    @Test
    @DisplayName("RN-501: o seed cria exatamente as 9 categorias de sistema de entities.md §6.10")
    void seedShouldCreateNineSystemCategories() {
        int created = asOwnerOfA(() -> categoryService.seedDefaults());
        List<CategoryResponse> categories = asOwnerOfA(() -> categoryService.list(null, null));

        assertThat(created).isEqualTo(9);
        assertThat(categories).hasSize(9);
        assertThat(categories).allSatisfy(category -> assertThat(category.isSystem()).isTrue());
        assertThat(categories)
                .extracting(CategoryResponse::name)
                .containsExactly(
                        "Desenvolvimento",
                        "Correção de Bug",
                        "Reunião",
                        "Suporte",
                        "Análise / Planejamento",
                        "Code Review",
                        "Documentação",
                        "Infraestrutura / Deploy",
                        "Interno (não faturável)");
        assertThat(categories.get(8).billableByDefault())
                .as("'Interno (não faturável)' é a única categoria não faturável do seed")
                .isFalse();
    }

    @Test
    @DisplayName("CX-14: o seed é idempotente — reexecutar não duplica o catálogo")
    void seedShouldBeIdempotent() {
        asOwnerOfA(() -> categoryService.seedDefaults());
        int second = asOwnerOfA(() -> categoryService.seedDefaults());

        assertThat(second).isZero();
        assertThat(asOwnerOfA(() -> categoryService.list(null, null))).hasSize(9);
    }

    @Test
    @DisplayName("RN-502/CX-01: nome duplicado sem diferenciar caixa é rejeitado com DEVTIME-2601")
    void shouldRejectDuplicateNameIgnoringCase() {
        asOwnerOfA(() -> categoryService.create(create("Consultoria")));

        assertThatThrownBy(() -> asOwnerOfA(() -> categoryService.create(create("CONSULTORIA"))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2601");
    }

    @Test
    @DisplayName("CX-02: nomes que diferem apenas por acento são categorias distintas")
    void shouldAcceptNamesDifferingByAccent() {
        asOwnerOfA(() -> categoryService.create(create("Análise")));
        asOwnerOfA(() -> categoryService.create(create("Analise")));

        assertThat(asOwnerOfA(() -> categoryService.list(null, null))).hasSize(2);
    }

    @Test
    @DisplayName("CX-03: o nome de uma categoria excluída pode ser reutilizado (índice parcial)")
    void shouldAllowReusingNameOfDeletedCategory() {
        UUID id = asOwnerOfA(() -> categoryService.create(create("Temporária")).id());
        asOwnerOfA(() -> categoryService.delete(id, null));

        CategoryResponse recreated = asOwnerOfA(() -> categoryService.create(create("Temporária")));

        assertThat(recreated.id()).isNotEqualTo(id);
    }

    @Test
    @DisplayName("RN-503: categoria de sistema não pode ser excluída — DEVTIME-2602")
    void shouldProtectSystemCategoryFromDeletion() {
        asOwnerOfA(() -> categoryService.seedDefaults());
        UUID systemId = asOwnerOfA(() -> categoryService.list(null, null)).get(0).id();

        assertThatThrownBy(() -> asOwnerOfA(() -> categoryService.delete(systemId, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2602");
    }

    @Test
    @DisplayName("RN-503: categoria de sistema pode ser renomeada e inativada")
    void shouldAllowRenamingAndDeactivatingSystemCategory() {
        asOwnerOfA(() -> categoryService.seedDefaults());
        CategoryResponse system = asOwnerOfA(() -> categoryService.list(null, null)).get(0);

        CategoryResponse updated =
                asOwnerOfA(
                        () ->
                                categoryService.update(
                                        system.id(),
                                        new CategoryUpdateRequest(
                                                "Engenharia",
                                                null,
                                                "#123456",
                                                null,
                                                true,
                                                false,
                                                0,
                                                system.version())));

        assertThat(updated.name()).isEqualTo("Engenharia");
        assertThat(updated.active()).isFalse();
        assertThat(updated.isSystem()).as("isSystem é imutável (INV-CAT-05)").isTrue();
    }

    @Test
    @DisplayName("CX-04: renomear categoria de sistema para nome existente é rejeitado")
    void shouldEnforceUniquenessWhenRenamingSystemCategory() {
        asOwnerOfA(() -> categoryService.seedDefaults());
        CategoryResponse system = asOwnerOfA(() -> categoryService.list(null, null)).get(0);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                categoryService.update(
                                                        system.id(),
                                                        new CategoryUpdateRequest(
                                                                "Reunião",
                                                                null,
                                                                null,
                                                                null,
                                                                true,
                                                                true,
                                                                0,
                                                                system.version()))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("CX-07: categoria substituta igual à excluída é rejeitada com DEVTIME-2605")
    void shouldRejectReplacementEqualToDeleted() {
        UUID id = asOwnerOfA(() -> categoryService.create(create("Descartável")).id());

        assertThatThrownBy(() -> asOwnerOfA(() -> categoryService.delete(id, id)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2605");
    }

    @Test
    @DisplayName("CX-08: categoria substituta inativa é rejeitada com DEVTIME-2605")
    void shouldRejectInactiveReplacement() {
        CategoryResponse target = asOwnerOfA(() -> categoryService.create(create("Alvo")));
        CategoryResponse replacement =
                asOwnerOfA(() -> categoryService.create(create("Substituta")));
        asOwnerOfA(
                () ->
                        categoryService.update(
                                replacement.id(),
                                new CategoryUpdateRequest(
                                        "Substituta",
                                        null,
                                        null,
                                        null,
                                        true,
                                        false,
                                        1,
                                        replacement.version())));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                categoryService.delete(
                                                        target.id(), replacement.id())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2605");
    }

    @Test
    @DisplayName("users.md §8.4: a reordenação é atômica e aplica a nova ordem")
    void shouldReorderAtomically() {
        UUID first = asOwnerOfA(() -> categoryService.create(create("Alfa")).id());
        UUID second = asOwnerOfA(() -> categoryService.create(create("Beta")).id());

        List<CategoryResponse> reordered =
                asOwnerOfA(
                        () ->
                                categoryService.reorder(
                                        new CategoryReorderRequest(List.of(second, first))));

        assertThat(reordered).extracting(CategoryResponse::id).containsExactly(second, first);
    }

    @Test
    @DisplayName(
            "CX-10/CX-11/CX-12: reordenação incompleta, duplicada ou de outro tenant é rejeitada")
    void shouldRejectInvalidReorder() {
        UUID first = asOwnerOfA(() -> categoryService.create(create("Alfa")).id());
        asOwnerOfA(() -> categoryService.create(create("Beta")));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                categoryService.reorder(
                                                        new CategoryReorderRequest(
                                                                List.of(first)))))
                .as("CX-10: lista incompleta")
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                categoryService.reorder(
                                                        new CategoryReorderRequest(
                                                                List.of(first, first)))))
                .as("CX-12: identificadores duplicados")
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                categoryService.reorder(
                                                        new CategoryReorderRequest(
                                                                List.of(
                                                                        first,
                                                                        UUID.randomUUID())))))
                .as("CX-11: identificador de outro tenant")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("BR-208/RN-002: categoria de outro tenant é invisível e resulta em 404")
    void shouldIsolateCategoriesBetweenTenants() {
        UUID categoryOfA = asOwnerOfA(() -> categoryService.create(create("Somente do A")).id());

        assertThat(asOwnerOfB(() -> categoryService.list(null, null)))
                .as("o filtro de tenant restringe a listagem")
                .isEmpty();
        assertThatThrownBy(() -> asOwnerOfB(() -> categoryService.getById(categoryOfA)))
                .as("recurso de outro tenant é indistinguível de inexistente (ART-024)")
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("permissions.md §7: MEMBER não gerencia categorias, mas consulta")
    void memberShouldNotManageCategories() {
        asOwnerOfA(() -> categoryService.seedDefaults());

        assertThat(runAs(tenantAId, userAId, Role.MEMBER, () -> categoryService.list(null, null)))
                .hasSize(9);
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        Role.MEMBER,
                                        () -> categoryService.create(create("Proibida"))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("users.md §8.3: exclusão sem substituta informada é aceita quando não há vínculos")
    void shouldDeleteWithoutReplacementWhenUnused() {
        UUID id = asOwnerOfA(() -> categoryService.create(create("Sem Vínculos")).id());

        var result = asOwnerOfA(() -> categoryService.delete(id, null));

        assertThat(result.migratedWorkLogs()).isZero();
        assertThat(result.migratedTo()).isNull();
    }

    @Test
    @DisplayName("users.md §8.3: substituta inexistente é rejeitada com DEVTIME-2605")
    void shouldRejectUnknownReplacement() {
        UUID id = asOwnerOfA(() -> categoryService.create(create("Alvo Inexistente")).id());

        assertThatThrownBy(() -> asOwnerOfA(() -> categoryService.delete(id, UUID.randomUUID())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2605");
    }

    @Test
    @DisplayName("users.md §8.1: o filtro active e a busca por nome restringem a listagem")
    void shouldFilterByActiveAndSearch() {
        asOwnerOfA(() -> categoryService.create(create("Análise Técnica")));
        asOwnerOfA(() -> categoryService.create(create("Reunião")));

        assertThat(asOwnerOfA(() -> categoryService.list(true, "analise")))
                .as("a busca ignora acentos e caixa")
                .hasSize(1);
        assertThat(asOwnerOfA(() -> categoryService.list(false, null))).isEmpty();
        assertThat(asOwnerOfA(() -> categoryService.listActive())).hasSize(2);
    }

    @Test
    @DisplayName("spec §22.2: requireActive devolve a categoria ativa para outras features")
    void requireActiveShouldReturnCategory() {
        UUID id = asOwnerOfA(() -> categoryService.create(create("Publicada")).id());

        assertThat(asOwnerOfA(() -> categoryService.requireActive(id)).id()).isEqualTo(id);
    }

    private CategoryCreateRequest create(String name) {
        return new CategoryCreateRequest(name, null, "#6366F1", "pi-code", true, null);
    }
}
