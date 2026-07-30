package com.devtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regras de dependência entre camadas e features (backend.md §5.1, AR-01 a AR-09).
 *
 * <p>CA-01 de backend.md: todas as regras {@code AR-XX} são verificadas por ArchUnit. Estas regras
 * existem para tornar as fronteiras do monólito modular <b>físicas e verificáveis</b> — sem elas,
 * "modular" é apenas intenção (ADR-027, DT-02).
 */
class LayeringRulesTest {

    private static final String ROOT = "com.devtime";

    private final JavaClasses production =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    @Test
    @DisplayName("AR-01: shared não depende de nenhuma feature")
    void sharedMustNotDependOnFeatures() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(ROOT + ".shared..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                ROOT + ".tenant..",
                                ROOT + ".user..",
                                ROOT + ".auth..",
                                ROOT + ".audit..",
                                ROOT + ".client..",
                                ROOT + ".contract..",
                                ROOT + ".ticket..",
                                ROOT + ".worklog..",
                                ROOT + ".timer..",
                                ROOT + ".category..",
                                ROOT + ".tag..",
                                ROOT + ".report..",
                                ROOT + ".notification..",
                                ROOT + ".attachment..",
                                ROOT + ".comment..",
                                ROOT + ".job..")
                        .because(
                                "shared é infraestrutura transversal; depender de uma feature inverteria"
                                        + " a direção da dependência e impediria extrair módulos (ADR-027)");
        rule.check(production);
    }

    @Test
    @DisplayName("AR-04: Controller não acessa Repository")
    void controllerMustNotAccessRepository() {
        ArchRule rule =
                noClasses()
                        .that()
                        .haveSimpleNameEndingWith("Controller")
                        .should()
                        .dependOnClassesThat()
                        .haveSimpleNameEndingWith("Repository")
                        .because(
                                "ART-060 fixa o fluxo Controller → Service → Repository; pular o Service"
                                        + " coloca regra de negócio no Controller (P-07)");
        rule.allowEmptyShould(true).check(production);
    }

    @Test
    @DisplayName("AR-05: Repository não acessa Service")
    void repositoryMustNotAccessService() {
        ArchRule rule =
                noClasses()
                        .that()
                        .haveSimpleNameEndingWith("Repository")
                        .should()
                        .dependOnClassesThat()
                        .haveSimpleNameEndingWith("Service")
                        .because("BR-006: repositório é acesso a dados, nunca orquestração");
        rule.allowEmptyShould(true).check(production);
    }

    @Test
    @DisplayName("AR-06: entidade JPA nunca aparece em assinatura de método de Controller")
    void controllerMustNotExposeEntities() {
        ArchRule rule =
                noClasses()
                        .that()
                        .haveSimpleNameEndingWith("Controller")
                        .should()
                        .dependOnClassesThat()
                        .areAnnotatedWith(jakarta.persistence.Entity.class)
                        .because(
                                "ART-061 / P-01: expor entidade JPA acopla o contrato da API ao schema e"
                                        + " vaza campos sensíveis como passwordHash (INV-USR-02)");
        rule.allowEmptyShould(true).check(production);
    }

    @Test
    @DisplayName("AR-07: nenhuma classe fora de shared.error lança RuntimeException genérica")
    void onlySharedErrorMayThrowGenericRuntimeException() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage(ROOT + ".shared.error..")
                        // Uma classe de exceção necessariamente invoca o construtor da superclasse;
                        // o alvo da regra é o código que *lança* RuntimeException genérica, não a
                        // definição de um tipo de exceção próprio.
                        .and()
                        .areNotAssignableTo(RuntimeException.class)
                        .should()
                        .callConstructor(RuntimeException.class, String.class)
                        .orShould()
                        .callConstructor(RuntimeException.class, String.class, Throwable.class)
                        .because(
                                "ER-03: toda exceção de negócio referencia um código DEVTIME-XXXX;"
                                        + " RuntimeException genérica cai em 500 sem código (EX-04)");
        rule.check(production);
    }

    @Test
    @DisplayName("AR-08: @Transactional só aparece em classes Service ou ServiceImpl")
    void transactionalOnlyOnServices() {
        ArchRule rule =
                classes()
                        .that()
                        .areAnnotatedWith(
                                org.springframework.transaction.annotation.Transactional.class)
                        .should()
                        .haveSimpleNameEndingWith("Service")
                        .orShould()
                        .haveSimpleNameEndingWith("ServiceImpl")
                        .because(
                                "ART-064 / BR-120: transação declarada em Controller estende o escopo"
                                        + " transacional à serialização HTTP; em Repository, fragmenta"
                                        + " a unidade de trabalho");
        rule.allowEmptyShould(true).check(production);
    }

    @Test
    @DisplayName("AR-09: não existe ciclo de dependência entre pacotes de feature")
    void featurePackagesMustBeAcyclic() {
        ArchRule rule =
                slices().matching(ROOT + ".(*)..")
                        .should()
                        .beFreeOfCycles()
                        .because(
                                "um ciclo torna impossível extrair qualquer um dos módulos envolvidos"
                                        + " (ADR-027, DT-03)");
        rule.check(production);
    }

    @Test
    @DisplayName("BR-069: Service nunca conhece a camada HTTP")
    void serviceMustNotKnowHttpLayer() {
        ArchRule rule =
                noClasses()
                        .that()
                        .haveSimpleNameEndingWith("ServiceImpl")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "jakarta.servlet..",
                                "org.springframework.http..",
                                "org.springframework.web..")
                        .because(
                                "um serviço que conhece HTTP não pode ser reusado por job nem por outra"
                                        + " feature sem carregar a borda web consigo");
        rule.allowEmptyShould(true).check(production);
    }
}
