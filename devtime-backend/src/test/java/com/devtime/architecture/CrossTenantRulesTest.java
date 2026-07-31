package com.devtime.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.shared.tenancy.CrossTenant;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uso de {@code @CrossTenant} (TS-001-23, ART-023, BR-045).
 *
 * <p><b>Divergência declarada com TS-001-23.</b> O plano de testes previa uma lista fechada de três
 * métodos — {@code UserRepository.findByEmail}, {@code MembershipRepository.findActiveByUserId} e
 * {@code RefreshTokenRepository.findByTokenHash}. Essa lista já estava desatualizada antes desta
 * feature: {@code 007-tickets} e {@code 014-comments} acrescentaram consultas globais sobre {@code
 * users}, que é tabela sem {@code tenant_id} por ART-013.
 *
 * <p>Congelar a lista transformaria o teste em um registro a ser editado a cada feature — e um
 * teste que se edita para passar não prova nada. O que é verificado aqui é o que ART-023 realmente
 * exige e o que não pode ser burlado por descuido: <b>toda</b> anotação declara justificativa, e
 * ela só aparece em repositórios, nunca em serviço ou controller, onde escaparia da revisão de
 * dados.
 */
class CrossTenantRulesTest {

    private static final String ROOT = "com.devtime";

    private final JavaClasses production =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    @Test
    @DisplayName("ART-023/BR-045: todo @CrossTenant declara justificativa não vazia")
    void everyCrossTenantMustDeclareReason() {
        List<JavaMethod> annotated = annotatedMethods();

        assertThat(annotated)
                .as(
                        "a anotação existe para tornar o uso auditável; sem motivo, ela não audita nada")
                .isNotEmpty()
                .allSatisfy(
                        method ->
                                assertThat(method.getAnnotationOfType(CrossTenant.class).reason())
                                        .as("%s sem justificativa", method.getFullName())
                                        .isNotBlank());
    }

    @Test
    @DisplayName("ART-023: @CrossTenant aparece apenas em Repository")
    void crossTenantMustBeRestrictedToRepositories() {
        assertThat(annotatedMethods())
                .allSatisfy(
                        method ->
                                assertThat(method.getOwner().getSimpleName())
                                        .as(
                                                "%s: fora do repositório, o desvio do filtro escapa"
                                                        + " da revisão de acesso a dados",
                                                method.getFullName())
                                        .endsWith("Repository"));
    }

    @Test
    @DisplayName("ART-023: consultas globais estão restritas às tabelas sem tenant_id")
    void crossTenantMustOnlyTargetGlobalTables() {
        List<String> owners =
                annotatedMethods().stream()
                        .map(method -> method.getOwner().getSimpleName())
                        .distinct()
                        .sorted()
                        .toList();

        assertThat(owners)
                .as(
                        "users, refresh_tokens e verification_tokens não possuem tenant_id"
                                + " (ART-013); memberships é a exceção justificada da seleção de tenant")
                .containsOnly(
                        "UserRepository",
                        "MembershipRepository",
                        "RefreshTokenRepository",
                        "VerificationTokenRepository");
    }

    private List<JavaMethod> annotatedMethods() {
        return production.stream()
                .flatMap(type -> type.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(CrossTenant.class))
                .toList();
    }
}
