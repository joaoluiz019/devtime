package com.devtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fronteiras entre features (backend.md §5.1, AR-02 e AR-03).
 *
 * <p>A comunicação entre features ocorre exclusivamente pela interface {@code *Service} pública ou
 * por evento de domínio (ART-065). Acessar o repositório, a entidade ou a implementação de outra
 * feature é o atalho que transforma um monólito modular em um monólito comum: a fronteira deixa de
 * existir e a extração futura passa a exigir reescrita (ADR-027, DT-03).
 */
class FeatureBoundaryRulesTest {

    private static final String ROOT = "com.devtime";

    /**
     * Features existentes. Uma nova feature precisa ser acrescentada aqui — deliberadamente manual,
     * para que criar uma feature seja uma decisão consciente e não um efeito colateral de criar um
     * pacote.
     */
    private static final List<String> FEATURES =
            List.of(
                    "tenant",
                    "user",
                    "auth",
                    "audit",
                    "category",
                    "client",
                    "contract",
                    "tag",
                    "ticket",
                    "comment",
                    "attachment",
                    "worklog",
                    "timer",
                    "notification",
                    "dashboard",
                    "report");

    private final JavaClasses production =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    @Test
    @DisplayName("AR-02: uma feature não acessa a entidade de outra feature")
    void featureMustNotAccessAnotherFeatureEntities() {
        FEATURES.forEach(
                feature ->
                        noClasses()
                                .that()
                                .resideInAPackage(ROOT + "." + feature + "..")
                                .should()
                                .dependOnClassesThat()
                                .resideInAnyPackage(domainPackagesOtherThan(feature))
                                .because(
                                        "ART-065: o acesso entre features passa pela interface *Service"
                                                + " pública ou por evento, nunca pela entidade")
                                .allowEmptyShould(true)
                                .check(production));
    }

    @Test
    @DisplayName("AR-02: uma feature não acessa o Repository de outra feature")
    void featureMustNotAccessAnotherFeatureRepositories() {
        FEATURES.forEach(
                feature ->
                        noClasses()
                                .that()
                                .resideInAPackage(ROOT + "." + feature + "..")
                                .should()
                                .dependOnClassesThat(
                                        JavaClass.Predicates.simpleNameEndingWith("Repository")
                                                // Interfaces do Spring Data também terminam em
                                                // "Repository"; a regra trata de features do
                                                // DevTime.
                                                .and(
                                                        JavaClass.Predicates.resideInAPackage(
                                                                ROOT + ".."))
                                                .and(
                                                        JavaClass.Predicates
                                                                .resideOutsideOfPackages(
                                                                        ROOT + "." + feature + "..",
                                                                        ROOT + ".shared..")))
                                .because(
                                        "BR-002: injetar o repositório de outra feature é o caminho mais"
                                                + " curto e o que destrói a fronteira")
                                .allowEmptyShould(true)
                                .check(production));
    }

    @Test
    @DisplayName("AR-03: uma feature não acessa a implementação de serviço de outra feature")
    void featureMustNotAccessAnotherFeatureImplementations() {
        FEATURES.forEach(
                feature ->
                        noClasses()
                                .that()
                                .resideInAPackage(ROOT + "." + feature + "..")
                                .should()
                                .dependOnClassesThat(
                                        JavaClass.Predicates.simpleNameEndingWith("ServiceImpl")
                                                .and(
                                                        JavaClass.Predicates.resideOutsideOfPackage(
                                                                ROOT + "." + feature + "..")))
                                .because(
                                        "BR-003: o contrato entre features é a interface, não a classe")
                                .allowEmptyShould(true)
                                .check(production));
    }

    private String[] domainPackagesOtherThan(String feature) {
        return FEATURES.stream()
                .filter(other -> !other.equals(feature))
                .map(other -> ROOT + "." + other + ".domain..")
                .toArray(String[]::new);
    }
}
