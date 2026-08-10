package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.dto.MemberRequests.InvitationRequest;
import com.devtime.tenant.dto.MemberResponses.MemberInvitationResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ciclo de convite de membro (RN-457, users.md §7.5).
 *
 * <p>O convite é o único caminho pelo qual alguém entra numa organização, e até aqui só a metade do
 * aceite — que pertence a {@code 001} — tinha teste. Emissão, reenvio, revogação e expiração não
 * eram exercitadas por suíte alguma: um convite que não expira é acesso concedido para sempre a
 * quem talvez nem trabalhe mais com o cliente.
 */
class InvitationIntegrationTest extends FeatureTestSupport {

    @Autowired private InvitationService invitationService;
    @Autowired private MembershipRepository repository;

    @Test
    @DisplayName(
            "§7.5: o convite nasce INVITED, com papel e prazo, e aparece na lista de pendentes")
    void inviteCreatesPendingMembership() {
        MemberInvitationResponse convite = asOwnerOfA(() -> convidar(email("novo"), Role.MEMBER));

        assertThat(convite.status().name()).isEqualTo(MembershipStatus.INVITED.name());
        assertThat(convite.role()).isEqualTo(Role.MEMBER);
        assertThat(convite.expiresAt())
                .as("RN-457: o convite tem prazo — sem ele, o acesso oferecido não caduca nunca")
                .isAfter(NOW);
        assertThat(asOwnerOfA(() -> invitationService.listPending()))
                .extracting(MemberInvitationResponse::id)
                .contains(convite.id());
    }

    @Test
    @DisplayName("CX-06: convidar duas vezes o mesmo e-mail não cria dois vínculos")
    void invitingTwiceDoesNotDuplicateMembership() {
        String endereco = email("repetido");
        MemberInvitationResponse primeiro = asOwnerOfA(() -> convidar(endereco, Role.MEMBER));

        assertThatThrownBy(() -> asOwnerOfA(() -> convidar(endereco, Role.MEMBER)))
                .as("a organização já ofereceu acesso a este endereço")
                .isInstanceOf(BusinessRuleException.class);

        assertThat(asOwnerOfA(() -> invitationService.listPending()))
                .extracting(MemberInvitationResponse::id)
                .containsOnlyOnce(primeiro.id());
    }

    @Test
    @DisplayName("RN-457: reenviar renova o prazo sem criar vínculo novo")
    void resendRenewsTheDeadline() {
        MemberInvitationResponse convite = asOwnerOfA(() -> convidar(email("reenvio"), Role.ADMIN));

        clock.advance(java.time.Duration.ofDays(3));
        MemberInvitationResponse reenviado =
                asOwnerOfA(() -> invitationService.resend(convite.id()));

        assertThat(reenviado.id()).isEqualTo(convite.id());
        assertThat(reenviado.expiresAt())
                .as("quem não viu o primeiro e-mail precisa de prazo cheio, não do que sobrou")
                .isAfter(convite.expiresAt());
    }

    @Test
    @DisplayName("§7.5: revogar retira o convite da lista de pendentes")
    void revokeRemovesTheInvitation() {
        MemberInvitationResponse convite =
                asOwnerOfA(() -> convidar(email("revogado"), Role.MEMBER));

        asOwnerOfA(
                () -> {
                    invitationService.revoke(convite.id());
                    return null;
                });

        assertThat(asOwnerOfA(() -> invitationService.listPending()))
                .extracting(MemberInvitationResponse::id)
                .doesNotContain(convite.id());
    }

    @Test
    @DisplayName("RN-457: passados 7 dias o job expira o convite pendente")
    void expiredInvitationsAreClosedByTheJob() {
        MemberInvitationResponse convite =
                asOwnerOfA(() -> convidar(email("expirado"), Role.MEMBER));

        clock.advance(java.time.Duration.ofDays(8));
        int expirados = asOwnerOfA(() -> invitationService.expirePending());

        assertThat(expirados).isPositive();
        assertThat(asOwnerOfA(() -> repository.findById(convite.id()).orElseThrow().getStatus()))
                .as("INVITED → REMOVED: o acesso oferecido e não aceito deixa de valer")
                .isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    @DisplayName("BR-185: reexecutar a expiração não expira nada de novo")
    void expirationIsConvergent() {
        asOwnerOfA(() -> convidar(email("convergente"), Role.MEMBER));
        clock.advance(java.time.Duration.ofDays(8));
        asOwnerOfA(() -> invitationService.expirePending());

        assertThat(asOwnerOfA(() -> invitationService.expirePending()))
                .as("o convite já expirado saiu da varredura")
                .isZero();
    }

    @Test
    @DisplayName("SG-01: convite de outro tenant não é alcançável")
    void invitationOfAnotherTenantIsNotReachable() {
        MemberInvitationResponse convite =
                asOwnerOfA(() -> convidar(email("isolado"), Role.MEMBER));

        assertThatThrownBy(() -> asOwnerOfB(() -> invitationService.resend(convite.id())))
                .as("o vínculo pertence ao tenant A e é inexistente para o B")
                .isInstanceOf(RuntimeException.class);
    }

    private MemberInvitationResponse convidar(String email, Role papel) {
        return invitationService.invite(
                new InvitationRequest(email, papel, "Bem-vindo à organização"));
    }

    /** E-mail único por teste: a tabela de contas é global e atravessa os tenants do cenário. */
    private String email(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
    }
}
