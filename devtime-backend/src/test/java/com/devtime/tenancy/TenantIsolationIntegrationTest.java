package com.devtime.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.CrossTenantWriteException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantContextNotInitializedException;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.support.FixedClockTestConfiguration;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.support.IntegrationTestSupport;
import com.devtime.tenant.MembershipRepository;
import com.devtime.tenant.TenantRepository;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Isolamento entre tenants — critério de saída F0-01 de {@code specs/implementation-order.md} §3.
 *
 * <p>Regra F0-01: nenhuma feature de {@code specs/} inicia antes deste teste estar verde.
 * Isolamento retroativo é a classe de retrabalho mais cara do projeto (RP-05), e um teste escrito
 * depois das features tende a provar o comportamento implementado em vez do exigido.
 *
 * <p>Cobre as camadas 2 e 3 da defesa em profundidade de {@code security.md} §6.1 — filtro
 * automático de leitura e rejeição de escrita cross-tenant. A camada 1 (origem do {@code tenantId}
 * apenas no JWT) é coberta por {@code TenantContextFilterIntegrationTest}.
 */
class TenantIsolationIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = FixedClockTestConfiguration.FIXED_INSTANT;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID userAId;
    private UUID userBId;
    private UUID membershipBId;

    @BeforeEach
    void createTwoIsolatedTenants() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantContext.clear();

        // Tenant e User não são tenant-scoped (ART-013): podem ser criados sem contexto.
        tenantAId =
                transactionTemplate.execute(
                        status ->
                                tenantRepository
                                        .save(FoundationDataBuilder.tenant("alfa-" + suffix))
                                        .getId());
        tenantBId =
                transactionTemplate.execute(
                        status ->
                                tenantRepository
                                        .save(FoundationDataBuilder.tenant("beta-" + suffix))
                                        .getId());
        userAId =
                transactionTemplate.execute(
                        status ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "alfa-" + suffix + "@exemplo.com", NOW))
                                        .getId());
        userBId =
                transactionTemplate.execute(
                        status ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "beta-" + suffix + "@exemplo.com", NOW))
                                        .getId());

        runAs(
                tenantAId,
                userAId,
                () ->
                        membershipRepository.save(
                                FoundationDataBuilder.membership(
                                        tenantAId, userAId, Role.OWNER, NOW)));
        membershipBId =
                runAs(
                                tenantBId,
                                userBId,
                                () ->
                                        membershipRepository.save(
                                                FoundationDataBuilder.membership(
                                                        tenantBId, userBId, Role.OWNER, NOW)))
                        .getId();
    }

    @AfterEach
    void clearContext() {
        tenantContext.clear();
    }

    @Test
    @DisplayName("TI-04: listagem no tenant A não retorna nenhum recurso do tenant B")
    void listingMustNotLeakOtherTenantRecords() {
        var visible = runAs(tenantAId, userAId, () -> membershipRepository.findAll());

        assertThat(visible)
                .as("o filtro tenantFilter deve restringir a consulta ao tenant da sessão")
                .isNotEmpty()
                .allSatisfy(membership -> assertThat(membership.getTenantId()).isEqualTo(tenantAId))
                .extracting(Membership::getId)
                .doesNotContain(membershipBId);
    }

    @Test
    @DisplayName("TI-04: buscar por ID um recurso do tenant B a partir do tenant A não o encontra")
    void readByIdMustNotFindOtherTenantRecord() {
        var found = runAs(tenantAId, userAId, () -> membershipRepository.findById(membershipBId));

        assertThat(found)
                .as(
                        "recurso de outro tenant é indistinguível de inexistente, resultando em 404 (ART-024)")
                .isEmpty();
    }

    @Test
    @DisplayName("TI-04: consulta derivada no tenant A não encontra o usuário do tenant B")
    void derivedQueryMustNotLeakOtherTenantRecords() {
        var found = runAs(tenantAId, userAId, () -> membershipRepository.findByUserId(userBId));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("TI-04: contagem por status não inclui registros do tenant B")
    void countMustNotIncludeOtherTenantRecords() {
        var active =
                runAs(
                        tenantAId,
                        userAId,
                        () -> membershipRepository.findByStatus(MembershipStatus.ACTIVE));

        assertThat(active)
                .as("o total de paginação não pode incluir recursos de outro tenant")
                .hasSize(1)
                .first()
                .extracting(Membership::getTenantId)
                .isEqualTo(tenantAId);
    }

    @Test
    @DisplayName("TI-04: escrita no tenant A não altera recurso do tenant B")
    void writeMustNotAffectOtherTenantRecord() {
        int updated =
                runAs(
                        tenantAId,
                        userAId,
                        () -> membershipRepository.softDelete(membershipBId, NOW, userAId));

        assertThat(updated).as("nenhuma linha do tenant B pode ser afetada").isZero();

        var stillPresent =
                runAs(tenantBId, userBId, () -> membershipRepository.findById(membershipBId));
        assertThat(stillPresent).as("o recurso do tenant B permanece inalterado").isPresent();
    }

    @Test
    @DisplayName("BR-041: gravar entidade com tenantId de outro tenant é rejeitado")
    void writeWithForeignTenantIdMustBeRejected() {
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        () ->
                                                membershipRepository.save(
                                                        FoundationDataBuilder.membership(
                                                                tenantBId,
                                                                userAId,
                                                                Role.MEMBER,
                                                                NOW))))
                .as("camada 3 de security.md §6.1: o AuditListener rejeita a escrita cross-tenant")
                .hasRootCauseInstanceOf(CrossTenantWriteException.class);
    }

    @Test
    @DisplayName("BR-040: entidade salva sem tenantId recebe o tenant da sessão, não nulo")
    void writeWithoutTenantIdMustInheritSessionTenant() {
        // INV-MEM-01 torna (tenantId, userId) único, então o novo membership precisa de outro
        // usuário: reaproveitar userA violaria a invariante em vez de exercitar a herança do
        // tenant.
        UUID newUserId =
                transactionTemplate.execute(
                        status ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "herda-"
                                                                + UUID.randomUUID()
                                                                + "@exemplo.com",
                                                        NOW))
                                        .getId());
        var membership = FoundationDataBuilder.membership(null, newUserId, Role.MEMBER, NOW);

        var saved = runAs(tenantAId, userAId, () -> membershipRepository.save(membership));

        assertThat(saved.getTenantId()).isEqualTo(tenantAId);
    }

    @Test
    @DisplayName(
            "TI-06: consulta tenant-scoped sem contexto de tenant falha em vez de retornar tudo")
    void writeWithoutTenantContextMustFail() {
        tenantContext.clear();

        assertThatThrownBy(
                        () ->
                                transactionTemplate.execute(
                                        status ->
                                                membershipRepository.save(
                                                        FoundationDataBuilder.membership(
                                                                null, userAId, Role.MEMBER, NOW))))
                .as("contexto vazio nunca degrada para 'todos os tenants' (CE-A-07)")
                .hasRootCauseInstanceOf(TenantContextNotInitializedException.class);
    }

    /** Executa uma operação como um usuário de um tenant, garantindo a limpeza do contexto. */
    private <T> T runAs(UUID tenantId, UUID userId, java.util.function.Supplier<T> operation) {
        tenantContext.set(
                new TenantSession(
                        userId,
                        tenantId,
                        UUID.randomUUID(),
                        Role.OWNER,
                        java.util.Set.of(Permission.MEMBER_VIEW),
                        "America/Sao_Paulo"));
        try {
            return transactionTemplate.execute(status -> operation.get());
        } finally {
            tenantContext.clear();
        }
    }

    private void runAs(UUID tenantId, UUID userId, Runnable operation) {
        runAs(
                tenantId,
                userId,
                () -> {
                    operation.run();
                    return null;
                });
    }
}
