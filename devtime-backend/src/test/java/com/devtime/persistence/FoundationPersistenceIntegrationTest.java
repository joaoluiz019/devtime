package com.devtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.support.FixedClockTestConfiguration;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.support.IntegrationTestSupport;
import com.devtime.tenant.MembershipRepository;
import com.devtime.tenant.TenantRepository;
import com.devtime.tenant.domain.Tenant;
import com.devtime.user.UserRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fundação de persistência: UUIDv7, auditoria, soft delete e concorrência otimista.
 *
 * <p>Critério de saída de F0 em {@code specs/implementation-order.md} §3: "Toda entidade herda e é
 * auditada".
 */
class FoundationPersistenceIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = FixedClockTestConfiguration.FIXED_INSTANT;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void clearContext() {
        tenantContext.clear();
    }

    @Test
    @DisplayName("ART-010: o identificador é um UUIDv7 gerado pela aplicação")
    void shouldAssignTimeOrderedUuid() {
        Tenant saved = save(FoundationDataBuilder.tenant("uuid-" + suffix()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version())
                .as("UUIDv7 preserva a localidade de índice B-Tree, ao contrário do UUIDv4")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("ART-011: dois identificadores gerados em sequência são crescentes")
    void generatedIdsMustBeTimeOrdered() {
        Tenant first = save(FoundationDataBuilder.tenant("ord-a-" + suffix()));
        Tenant second = save(FoundationDataBuilder.tenant("ord-b-" + suffix()));

        assertThat(first.getId().toString()).isLessThan(second.getId().toString());
    }

    @Test
    @DisplayName("ART-050: createdAt e updatedAt vêm do Clock injetado, não do relógio do sistema")
    void auditFieldsMustComeFromInjectedClock() {
        Tenant saved = save(FoundationDataBuilder.tenant("audit-" + suffix()));

        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @DisplayName("ART-050: createdBy é preenchido com o usuário da sessão")
    void createdByMustComeFromSession() {
        var tenantId = save(FoundationDataBuilder.tenant("actor-" + suffix())).getId();
        var userId = saveUser("actor-" + suffix() + "@exemplo.com");

        var membership =
                runAs(
                        tenantId,
                        userId,
                        () ->
                                membershipRepository.save(
                                        FoundationDataBuilder.membership(
                                                null, userId, Role.OWNER, NOW)));

        assertThat(membership.getCreatedBy()).isEqualTo(userId);
        assertThat(membership.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("ART-050: createdBy é nulo em criação de sistema, sem sessão")
    void createdByMustBeNullForSystemCreation() {
        Tenant saved = save(FoundationDataBuilder.tenant("system-" + suffix()));

        assertThat(saved.getCreatedBy())
                .as("entities.md §4.1: nulo apenas em criações de sistema")
                .isNull();
    }

    @Test
    @DisplayName("ART-051: a exclusão é lógica e o registro deixa de ser retornado nas consultas")
    void deleteMustBeLogical() {
        var tenantId = save(FoundationDataBuilder.tenant("soft-" + suffix())).getId();
        var userId = saveUser("soft-" + suffix() + "@exemplo.com");
        var membershipId =
                runAs(
                                tenantId,
                                userId,
                                () ->
                                        membershipRepository.save(
                                                FoundationDataBuilder.membership(
                                                        null, userId, Role.MEMBER, NOW)))
                        .getId();

        int affected =
                runAs(
                        tenantId,
                        userId,
                        () -> membershipRepository.softDelete(membershipId, NOW, userId));

        assertThat(affected).isEqualTo(1);
        assertThat(runAs(tenantId, userId, () -> membershipRepository.findById(membershipId)))
                .as(
                        "@SQLRestriction(\"deleted_at IS NULL\") remove o registro das consultas (BR-029)")
                .isEmpty();
    }

    @Test
    @DisplayName("ART-051: soft delete repetido não afeta nenhuma linha")
    void repeatedSoftDeleteMustBeIdempotent() {
        var tenantId = save(FoundationDataBuilder.tenant("idem-" + suffix())).getId();
        var userId = saveUser("idem-" + suffix() + "@exemplo.com");
        var membershipId =
                runAs(
                                tenantId,
                                userId,
                                () ->
                                        membershipRepository.save(
                                                FoundationDataBuilder.membership(
                                                        null, userId, Role.MEMBER, NOW)))
                        .getId();

        runAs(tenantId, userId, () -> membershipRepository.softDelete(membershipId, NOW, userId));
        int second =
                runAs(
                        tenantId,
                        userId,
                        () -> membershipRepository.softDelete(membershipId, NOW, userId));

        assertThat(second).isZero();
    }

    @Test
    @DisplayName("ART-055: o índice único parcial permite recadastrar um slug após exclusão lógica")
    void partialUniqueIndexMustAllowReuseAfterSoftDelete() {
        String slug = "reuse-" + suffix();
        var first = save(FoundationDataBuilder.tenant(slug));

        transactionTemplate.execute(
                status -> tenantRepository.softDelete(first.getId(), NOW, null));
        var recreated = save(FoundationDataBuilder.tenant(slug));

        assertThat(recreated.getId())
                .as("sem o índice parcial, o registro excluído bloquearia o recadastro (CE-DB-01)")
                .isNotEqualTo(first.getId());
    }

    @Test
    @DisplayName("ART-052: toda entidade nasce com version 0 para optimistic locking")
    void entitiesMustStartAtVersionZero() {
        var userId = saveUser("version-" + suffix() + "@exemplo.com");

        assertThat(userRepository.findById(userId))
                .isPresent()
                .get()
                .extracting("version")
                .isEqualTo(0L);
    }

    private Tenant save(Tenant tenant) {
        return transactionTemplate.execute(status -> tenantRepository.save(tenant));
    }

    private UUID saveUser(String email) {
        return transactionTemplate.execute(
                status -> userRepository.save(FoundationDataBuilder.user(email, NOW)).getId());
    }

    private <T> T runAs(UUID tenantId, UUID userId, java.util.function.Supplier<T> operation) {
        tenantContext.set(
                new TenantSession(
                        userId,
                        tenantId,
                        UUID.randomUUID(),
                        Role.OWNER,
                        Set.of(Permission.MEMBER_VIEW),
                        "America/Sao_Paulo"));
        try {
            return transactionTemplate.execute(status -> operation.get());
        } finally {
            tenantContext.clear();
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
