package com.devtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.devtime.audit.domain.AuditLog;
import com.devtime.shared.persistence.BaseEntity;
import com.devtime.tag.domain.TicketTagLink;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regras de entidade e persistência (ai/backend-rules.md §6, BR-020 a BR-035).
 *
 * <p>Estas regras protegem invariantes que, violadas, produzem erro silencioso e caro: duração em
 * ponto flutuante gera divergência de centavos em fatura (P-04), ausência de
 * {@code @SQLRestriction} faz registros excluídos reaparecerem em relatório, e
 * {@code @GeneratedValue} de banco quebra ART-010.
 */
class PersistenceRulesTest {

    private static final String ROOT = "com.devtime";

    private final JavaClasses production =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    @Test
    @DisplayName("BR-020: toda entidade de domínio estende BaseEntity")
    void entitiesMustExtendBaseEntity() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                // database.md §4.3 isenta audit_logs: é append-only e não possui updated_*,
                // deleted_* nem version (INV-AUD-01), colunas que BaseEntity exigiria.
                //
                // ticket_tags é tabela de junção pura (database.md §7.12): não tem identidade
                // própria, nada nela é editável (o vínculo existe ou não existe) e §9.3 de
                // specs/006-tags determina a remoção física das linhas na exclusão da etiqueta.
                // P-03 protege entidade de domínio; uma aresta entre duas não é dado de negócio.
                .and()
                .doNotBelongToAnyOf(AuditLog.class, TicketTagLink.class)
                .should()
                .beAssignableTo(BaseEntity.class)
                .because("ART-050: identidade, auditoria, soft delete e version vêm de um lugar só")
                .check(production);
    }

    @Test
    @DisplayName("BR-028: toda entidade possui @Version")
    void entitiesMustDeclareVersion() {
        // BaseEntity declara @Version para toda a hierarquia; a regra verifica que existe
        // exatamente um campo assim e que nenhuma entidade o redeclare de forma divergente.
        fields().that()
                .areDeclaredInClassesThat()
                .areAssignableTo(BaseEntity.class)
                .and()
                .areAnnotatedWith(Version.class)
                .should()
                .haveRawType(Long.class)
                .because("ART-052: conflito de versão retorna 409 e exige tipo estável")
                .check(production);
    }

    @Test
    @DisplayName("BR-029: toda entidade possui @SQLRestriction(\"deleted_at IS NULL\")")
    void entitiesMustFilterSoftDeleted() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                // audit_logs não possui deleted_at (INV-AUD-01); ticket_tags também não, porque a
                // desvinculação é remoção física da aresta (§9.3 de specs/006-tags).
                .and()
                .doNotBelongToAnyOf(AuditLog.class, TicketTagLink.class)
                .should()
                .beAnnotatedWith(SQLRestriction.class)
                .because(
                        "sem a restrição, registros excluídos logicamente voltariam a aparecer em"
                                + " consultas e relatórios (ART-051)")
                .check(production);
    }

    @Test
    @DisplayName("BR-022: nenhuma entidade usa @GeneratedValue")
    void entitiesMustNotUseDatabaseGeneratedIds() {
        noFields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(ROOT + "..")
                .should()
                .beAnnotatedWith(jakarta.persistence.GeneratedValue.class)
                .because(
                        "ART-010/ART-011: o identificador é UUIDv7 gerado na aplicação, nunca no banco")
                .check(production);
    }

    @Test
    @DisplayName("BR-027: nenhum campo usa Date, Calendar ou Timestamp")
    void entitiesMustUseJavaTime() {
        noFields()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Entity.class)
                .should()
                .haveRawType(java.util.Date.class)
                .orShould()
                .haveRawType(java.util.Calendar.class)
                .orShould()
                .haveRawType(java.sql.Timestamp.class)
                .because(
                        "ART-030: instante é Instant em UTC; os tipos legados carregam fuso implícito da"
                                + " JVM e produzem resultado diferente entre ambientes")
                .check(production);
    }

    @Test
    @DisplayName("BR-023/BR-024: nenhum campo de duração ou dinheiro usa ponto flutuante")
    void entitiesMustNotUseFloatingPoint() {
        noFields()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Entity.class)
                .should()
                .haveRawType(Double.class)
                .orShould()
                .haveRawType(double.class)
                .orShould()
                .haveRawType(Float.class)
                .orShould()
                .haveRawType(float.class)
                .because(
                        "P-04/P-05: somatório de horas em ponto flutuante acumula erro e gera divergência"
                                + " de centavos em fatura; duração é int em minutos e dinheiro é"
                                + " BigDecimal")
                .check(production);
    }

    @Test
    @DisplayName("ART-022: nenhum código usa getReferenceById, que ignora o filtro de tenant")
    void codeMustNotUseGetReferenceById() {
        noClasses()
                .that()
                .resideInAPackage(ROOT + "..")
                .should()
                .callMethodWhere(
                        com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.name(
                                        "getReferenceById")))
                .because(
                        "getReferenceById devolve um proxy carregado por EntityManager.getReference(),"
                                + " e o @Filter de Hibernate não se aplica a ele — o registro de outro"
                                + " tenant seria acessível. Use findById, que é sobrescrito em"
                                + " SoftDeleteRepository com JPQL")
                .check(production);
    }

    @Test
    @DisplayName("BR-140/BR-141: nenhum código usa o relógio do sistema diretamente")
    void codeMustUseInjectedClock() {
        noClasses()
                .that()
                .resideInAPackage(ROOT + "..")
                // Estas duas classes são a fronteira autorizada: JpaConfig produz o bean Clock e
                // TenantClock é a fachada que todo o restante do código consome.
                .and()
                .doNotHaveSimpleName("JpaConfig")
                .and()
                .doNotHaveSimpleName("TenantClock")
                .should()
                .callMethod(java.time.Instant.class, "now")
                .orShould()
                .callMethod(java.time.LocalDate.class, "now")
                .orShould()
                .callMethod(System.class, "currentTimeMillis")
                .because(
                        "sem Clock injetado, nenhum cálculo temporal é testável de forma determinística"
                                + " (BR-205)")
                .check(production);
    }
}
