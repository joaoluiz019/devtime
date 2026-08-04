package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.FeatureTestSupport;
import com.devtime.tenant.domain.TenantStatus;
import com.devtime.user.UserAccountService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RN-008 e §19.1 de specs/001: purga da organização cancelada e anonimização das contas órfãs.
 *
 * <p>É a única operação do sistema que torna dado pessoal inalcançável, e por isso o teste verifica
 * as duas metades: o que some (a organização, o e-mail, o nome) e o que <b>permanece</b> — a conta
 * de quem participa de outra organização, que não pode ser atingida pela purga da primeira.
 */
class TenantPurgeIntegrationTest extends FeatureTestSupport {

    @Autowired private TenantService tenantService;
    @Autowired private UserAccountService userAccountService;

    @Test
    @DisplayName("RN-008: tenant cancelado há mais de 30 dias é purgado e a conta é anonimizada")
    void purgeAnonymizesUsersWithoutOtherTenants() {
        String originalEmail = emailOf(userAId);
        scheduleImmediatePurge(tenantAId);

        int purged = tenantService.purgeExpiredCancellations();

        assertThat(purged).isEqualTo(1);
        assertThat(emailOf(userAId))
                .as("§19.1: e-mail substituído por endereço em domínio não roteável")
                .isNotEqualTo(originalEmail)
                .endsWith("@anonimizado.local");
        assertThat(userAccountService.require(userAId).fullName()).isEqualTo("Usuário Removido");
    }

    @Test
    @DisplayName("§19.1: a conta de quem participa de outra organização não é anonimizada")
    void purgeMustNotTouchUsersWithAnotherTenant() {
        // O mesmo usuário passa a ter vínculo também no tenant B, que não está sendo purgado.
        runAs(
                tenantBId,
                userAId,
                com.devtime.shared.security.Role.MEMBER,
                () ->
                        membershipRepository.save(
                                com.devtime.support.FoundationDataBuilder.membership(
                                        tenantBId,
                                        userAId,
                                        com.devtime.shared.security.Role.MEMBER,
                                        NOW)));
        String originalEmail = emailOf(userAId);
        scheduleImmediatePurge(tenantAId);

        tenantService.purgeExpiredCancellations();

        assertThat(emailOf(userAId))
                .as("a retenção da outra organização ainda vale para esta pessoa")
                .isEqualTo(originalEmail);
    }

    @Test
    @DisplayName("BR-185: reexecutar a purga não anonimiza a mesma conta duas vezes")
    void purgeIsConvergent() {
        scheduleImmediatePurge(tenantAId);
        tenantService.purgeExpiredCancellations();

        assertThat(tenantService.purgeExpiredCancellations())
                .as("o tenant já purgado saiu da varredura")
                .isZero();
    }

    /** Cancela o tenant e antecipa a purga, sem esperar os 30 dias de retenção. */
    private void scheduleImmediatePurge(UUID tenantId) {
        inTransaction(
                () -> {
                    var tenant = tenantRepository.findById(tenantId).orElseThrow();
                    tenant.setStatus(TenantStatus.CANCELLED);
                    tenant.setCancelledAt(NOW.minus(Duration.ofDays(31)));
                    tenant.setPurgeScheduledAt(NOW.minus(Duration.ofDays(1)));
                    return tenantRepository.save(tenant);
                });
    }

    private String emailOf(UUID userId) {
        return userAccountService.require(userId).email();
    }
}
