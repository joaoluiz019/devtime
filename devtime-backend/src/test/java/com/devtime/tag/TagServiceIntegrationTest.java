package com.devtime.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.support.FeatureTestSupport;
import com.devtime.tag.dto.TagRequests.TagCreateRequest;
import com.devtime.tag.dto.TagRequests.TagUpdateRequest;
import com.devtime.tag.dto.TagResponses.TagResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Regras do vocabulário de etiquetas (RN-506 a RN-508, spec 006). */
class TagServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private TagService tagService;

    @Test
    @DisplayName("RN-506: a resposta da criação traz o nome já normalizado")
    void shouldReturnNormalizedName() {
        TagResponse tag =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("Code Review", null)));

        assertThat(tag.name())
                .as(
                        "o usuário precisa ver o que foi de fato criado, sob pena de a normalização"
                                + " parecer defeito")
                .isEqualTo("code-review");
        assertThat(tag.usageCount()).isZero();
        assertThat(tag.color()).isEqualTo("#94A3B8");
    }

    @Test
    @DisplayName(
            "RN-507/CX-01: entradas que normalizam para o mesmo valor colidem com DEVTIME-2604")
    void shouldRejectDuplicateNormalizedName() {
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("Code Review", null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                tagService.create(
                                                        new TagCreateRequest("code-review", null))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2604");
    }

    @Test
    @DisplayName("CX-02: refatoração e refatoracao coexistem — acentos são preservados")
    void shouldAllowNamesDifferingByAccent() {
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("refatoração", null)));
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("refatoracao", null)));

        assertThat(asOwnerOfA(() -> tagService.search(null, null))).hasSize(2);
    }

    @Test
    @DisplayName("RN-507/CX-03: nome que normaliza para menos de 2 caracteres é rejeitado")
    void shouldRejectTooShortNormalizedName() {
        assertThatThrownBy(
                        () -> asOwnerOfA(() -> tagService.create(new TagCreateRequest("a", null))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2000");
    }

    @Test
    @DisplayName("RN-507/CX-04: nome com 41 caracteres após normalização é rejeitado")
    void shouldRejectTooLongNormalizedName() {
        String fortyOne = "a".repeat(41);

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                tagService.create(
                                                        new TagCreateRequest(fortyOne, null))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2000");
    }

    @Test
    @DisplayName("CX-05: 60 caracteres com espaços encolhem para dentro do limite e são aceitos")
    void shouldAcceptWhitespaceHeavyNameThatShrinks() {
        String raw = "Sprint     2026     Q1     Plano     Geral";

        TagResponse tag = asOwnerOfA(() -> tagService.create(new TagCreateRequest(raw, null)));

        assertThat(tag.name()).isEqualTo("sprint-2026-q1-plano-geral");
        assertThat(tag.name().length()).isLessThanOrEqualTo(40);
    }

    @Test
    @DisplayName("RN-506/RN-507: resolveOrCreate é idempotente para entradas equivalentes")
    void resolveOrCreateShouldBeIdempotent() {
        UUID first = asOwnerOfA(() -> tagService.resolveOrCreate("Code Review"));
        UUID second = asOwnerOfA(() -> tagService.resolveOrCreate("  code-review "));

        assertThat(second).isEqualTo(first);
        assertThat(asOwnerOfA(() -> tagService.search(null, null))).hasSize(1);
    }

    @Test
    @DisplayName("CX-09: renomear para o nome de outra etiqueta existente é rejeitado")
    void shouldRejectRenameToExistingName() {
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));
        TagResponse other =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("critico", null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                tagService.update(
                                                        other.id(),
                                                        new TagUpdateRequest(
                                                                "URGENTE", null, other.version()))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2604");
    }

    @Test
    @DisplayName("RN-004: renomear com version divergente retorna DEVTIME-2004")
    void shouldRejectStaleVersionOnUpdate() {
        TagResponse tag =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                tagService.update(
                                                        tag.id(),
                                                        new TagUpdateRequest(
                                                                "urgentissimo", null, 99L))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2004");
    }

    @Test
    @DisplayName("CX-08: etiqueta excluída pode ser recriada, e nasce com usageCount zerado")
    void shouldAllowRecreatingDeletedTag() {
        TagResponse tag =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));
        asOwnerOfA(() -> tagService.delete(tag.id()));

        TagResponse recreated =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));

        assertThat(recreated.id()).isNotEqualTo(tag.id());
        assertThat(recreated.usageCount()).isZero();
    }

    @Test
    @DisplayName("§9.3: a exclusão responde com as contagens desvinculadas, não 204")
    void deleteShouldReportUnlinkCounts() {
        TagResponse tag =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));

        var response = asOwnerOfA(() -> tagService.delete(tag.id()));

        assertThat(response.unlinkedFromTickets()).isZero();
        assertThat(response.unlinkedFromWorkLogs()).isZero();
        assertThat(asOwnerOfA(() -> tagService.search(null, null))).isEmpty();
    }

    @Test
    @DisplayName("RN-508/CX-14: etiqueta órfã recém-criada não é sugerida — o limiar é de 90 dias")
    void shouldNotSuggestRecentOrphans() {
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));

        assertThat(asOwnerOfA(() -> tagService.cleanupSuggestions()).tags()).isEmpty();
    }

    @Test
    @DisplayName("§20: o autocompletar devolve no máximo 20 resultados, limitados no servidor")
    void autocompleteShouldLimitResults() {
        asOwnerOfA(
                () -> {
                    for (int index = 0; index < 25; index++) {
                        tagService.create(new TagCreateRequest("assunto-" + index, null));
                    }
                    return null;
                });

        assertThat(asOwnerOfA(() -> tagService.autocomplete("assunto"))).hasSize(20);
    }

    @Test
    @DisplayName("RN-002/SG-01: etiqueta de outro tenant responde 404, nunca 403")
    void shouldNotReachTagFromAnotherTenant() {
        TagResponse tag =
                asOwnerOfA(() -> tagService.create(new TagCreateRequest("urgente", null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfB(
                                        () ->
                                                tagService.update(
                                                        tag.id(),
                                                        new TagUpdateRequest(
                                                                "outro", null, tag.version()))))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(asOwnerOfB(() -> tagService.search(null, null)))
                .as("o tenant B não enxerga o vocabulário do tenant A")
                .isEmpty();
    }

    @Test
    @DisplayName("SG-04: nome com payload de XSS é armazenado como texto, sem interpretação")
    void shouldStoreScriptPayloadAsPlainText() {
        TagResponse tag =
                asOwnerOfA(
                        () ->
                                tagService.create(
                                        new TagCreateRequest("<script>alert(1)</script>", null)));

        assertThat(tag.name()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    @DisplayName("users.md §9.1: a listagem ordena por uso decrescente e desempata por nome")
    void searchShouldOrderByUsageThenName() {
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("beta", null)));
        asOwnerOfA(() -> tagService.create(new TagCreateRequest("alfa", null)));

        List<TagResponse> tags = asOwnerOfA(() -> tagService.search(null, null));

        assertThat(tags).extracting(TagResponse::name).containsExactly("alfa", "beta");
    }
}
