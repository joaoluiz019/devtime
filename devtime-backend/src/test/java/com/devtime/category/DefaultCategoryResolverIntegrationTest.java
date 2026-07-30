package com.devtime.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.category.domain.Category;
import com.devtime.category.dto.CategoryRequests.CategoryCreateRequest;
import com.devtime.category.dto.CategoryRequests.CategoryUpdateRequest;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.support.FeatureTestSupport;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cadeia de pré-seleção da categoria (RN-104, §6.2 da spec 005 — T-005-20).
 *
 * <p>As quatro origens são exercitadas na ordem documentada, incluindo o salto de origem inativa e
 * a repetição que prova o determinismo — sem ele, dois registros feitos em sequência poderiam
 * receber categorias diferentes sem que nada tivesse mudado.
 */
class DefaultCategoryResolverIntegrationTest extends FeatureTestSupport {

    @Autowired private DefaultCategoryResolver resolver;
    @Autowired private CategoryService categoryService;

    @Test
    @DisplayName("RN-104 origem 1: a categoria do ticket tem precedência sobre as demais")
    void shouldPreferTicketCategory() {
        UUID ticketCategory = create("Do Ticket");
        UUID contractCategory = create("Do Contrato");
        UUID userCategory = create("Do Usuário");

        assertThat(resolve(ticketCategory, contractCategory, userCategory))
                .isEqualTo(ticketCategory);
    }

    @Test
    @DisplayName("RN-104 origem 2: sem categoria no ticket, vale a do contrato")
    void shouldFallBackToContractCategory() {
        UUID contractCategory = create("Do Contrato");
        UUID userCategory = create("Do Usuário");

        assertThat(resolve(null, contractCategory, userCategory)).isEqualTo(contractCategory);
    }

    @Test
    @DisplayName("RN-104 origem 3: sem ticket nem contrato, vale a preferência do usuário")
    void shouldFallBackToUserCategory() {
        UUID userCategory = create("Do Usuário");

        assertThat(resolve(null, null, userCategory)).isEqualTo(userCategory);
    }

    @Test
    @DisplayName("RN-104 origem 4: sem nenhuma origem, vale a primeira ativa por sortOrder e nome")
    void shouldFallBackToFirstActiveCategory() {
        asOwnerOfA(() -> categoryService.seedDefaults());

        UUID first = asOwnerOfA(() -> categoryService.list(true, null)).get(0).id();

        assertThat(resolve(null, null, null)).isEqualTo(first);
    }

    @Test
    @DisplayName("§6.2: origem inativa é pulada, não rejeitada")
    void shouldSkipInactiveOrigin() {
        UUID inactive = create("Inativada");
        deactivate(inactive);
        UUID contractCategory = create("Do Contrato");

        assertThat(resolve(inactive, contractCategory, null))
                .as("um ticket que aponta para categoria inativada não impede o registro de horas")
                .isEqualTo(contractCategory);
    }

    @Test
    @DisplayName("CX-06: sem nenhuma categoria ativa, a resolução devolve vazio")
    void shouldReturnEmptyWithoutActiveCategories() {
        UUID only = create("Única");
        deactivate(only);

        assertThat(asOwnerOfA(() -> resolver.resolveDefault(null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("§6.2: a resolução é determinística em repetições")
    void shouldBeDeterministic() {
        asOwnerOfA(() -> categoryService.seedDefaults());
        UUID first = resolve(null, null, null);

        for (int repetition = 0; repetition < 20; repetition++) {
            assertThat(resolve(null, null, null)).isEqualTo(first);
        }
    }

    private UUID resolve(UUID ticket, UUID contract, UUID user) {
        Optional<Category> resolved =
                asOwnerOfA(() -> resolver.resolveDefault(ticket, contract, user));
        return resolved.orElseThrow().getId();
    }

    private UUID create(String name) {
        return asOwnerOfA(
                        () ->
                                categoryService.create(
                                        new CategoryCreateRequest(
                                                name, null, null, null, true, null)))
                .id();
    }

    private void deactivate(UUID id) {
        CategoryResponse category = asOwnerOfA(() -> categoryService.getById(id));
        asOwnerOfA(
                () ->
                        categoryService.update(
                                id,
                                new CategoryUpdateRequest(
                                        category.name(),
                                        null,
                                        null,
                                        null,
                                        true,
                                        false,
                                        category.sortOrder(),
                                        category.version())));
    }
}
