package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.auth.AuthService.SessionOutcome;
import com.devtime.auth.dto.AuthRequests.LoginRequest;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthRequests.SelectTenantRequest;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.JwtService;
import com.devtime.shared.security.Role;
import com.devtime.support.FixedClockTestConfiguration;
import com.devtime.tenant.MembershipService;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Seleção de organização e gestão de sessões (TS-001-13, AC-001-04, AC-001-05, AC-001-20,
 * AC-001-29, AC-001-38).
 */
class TenantSelectionAndSessionIntegrationTest extends AuthTestSupport {

    @Autowired private JwtService jwtService;
    @Autowired private SessionService sessionService;
    @Autowired private MembershipService membershipService;

    @Test
    @DisplayName("AC-001-04: com dois vínculos ativos, o login devolve token de pré-seleção")
    void multiTenantLoginMustRequireSelection() {
        Fixture fixture = userWithTwoTenants();

        SessionOutcome outcome =
                authService.login(new LoginRequest(fixture.email(), VALID_PASSWORD), metadata());

        assertThat(outcome.response().tenantSelectionRequired()).isTrue();
        assertThat(outcome.response().tenants()).hasSize(2);
        assertThat(outcome.response().tenant()).isNull();
        assertThat(jwtService.parse(outcome.response().accessToken()).tenantId())
                .as("CE-P-11: sem claim tid, nenhum endpoint de negócio responde")
                .isNull();
    }

    @Test
    @DisplayName("AC-001-05: a seleção completa a sessão com claim tid, papel e permissões")
    void selectTenantMustCompleteSession() {
        Fixture fixture = userWithTwoTenants();
        SessionOutcome preAuth =
                authService.login(new LoginRequest(fixture.email(), VALID_PASSWORD), metadata());

        SessionOutcome selected =
                authService.selectTenant(
                        new SelectTenantRequest(fixture.secondTenantId()),
                        fixture.userId(),
                        preAuth.rawRefreshToken());

        assertThat(selected.response().tenantSelectionRequired()).isFalse();
        assertThat(selected.response().role()).isEqualTo(Role.MEMBER);
        assertThat(selected.response().permissions()).isNotEmpty();
        assertThat(jwtService.parse(selected.response().accessToken()).tenantId())
                .isEqualTo(fixture.secondTenantId());
    }

    @Test
    @DisplayName("RN-459/AC-001-20: vínculo suspenso devolve DEVTIME-1102 na seleção")
    void suspendedMembershipMustBeRejected() {
        Fixture fixture = userWithTwoTenants();
        suspendMembership(fixture.secondTenantId(), fixture.userId(), fixture.secondMembershipId());

        assertThatThrownBy(
                        () ->
                                authService.selectTenant(
                                        new SelectTenantRequest(fixture.secondTenantId()),
                                        fixture.userId(),
                                        null))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.MEMBERSHIP_INACTIVE);
    }

    @Test
    @DisplayName("ART-024: selecionar organização da qual não se é membro devolve 404")
    void selectingForeignTenantMustReturnNotFound() {
        Fixture fixture = userWithTwoTenants();

        assertThatThrownBy(
                        () ->
                                authService.selectTenant(
                                        new SelectTenantRequest(UUID.randomUUID()),
                                        fixture.userId(),
                                        null))
                .as("403 confirmaria que o identificador existe")
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("RN-008: organização cancelada não abre sessão, mesmo com vínculo ativo")
    void cancelledTenantMustBlockSelection() {
        Fixture fixture = userWithTwoTenants();
        transactionTemplate.executeWithoutResult(
                status -> {
                    var tenant = tenantRepository.findById(fixture.secondTenantId()).orElseThrow();
                    tenant.setStatus(TenantStatus.CANCELLED);
                    tenantRepository.save(tenant);
                });

        assertThatThrownBy(
                        () ->
                                authService.selectTenant(
                                        new SelectTenantRequest(fixture.secondTenantId()),
                                        fixture.userId(),
                                        null))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_CANCELLED);
    }

    @Test
    @DisplayName("CX-08/AC-001-29: organização suspensa aparece marcada e continua selecionável")
    void suspendedTenantMustRemainSelectableInReadOnly() {
        Fixture fixture = userWithTwoTenants();
        transactionTemplate.executeWithoutResult(
                status -> {
                    var tenant = tenantRepository.findById(fixture.secondTenantId()).orElseThrow();
                    tenant.setStatus(TenantStatus.SUSPENDED);
                    tenantRepository.save(tenant);
                });

        var options = authService.listTenants(fixture.userId());

        assertThat(options)
                .as("omitir faria o usuário concluir que perdeu o acesso")
                .anyMatch(option -> "SUSPENDED".equals(option.status()));
        assertThat(
                        authService
                                .selectTenant(
                                        new SelectTenantRequest(fixture.secondTenantId()),
                                        fixture.userId(),
                                        null)
                                .response()
                                .accessToken())
                .isNotBlank();
    }

    @Test
    @DisplayName("§5.11: a listagem marca a sessão corrente e mascara o IP")
    void sessionListingMustMarkCurrentAndMaskIp() {
        RegisterRequest request = registerRequest("sessoes");
        UUID userId = registerAndVerify(request);
        SessionOutcome first =
                authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());
        authService.login(new LoginRequest(request.email(), VALID_PASSWORD), metadata());
        UUID currentId = refreshTokenService.identify(first.rawRefreshToken()).orElseThrow();
        authenticateAs(userId);

        var sessions = sessionService.listOwn(userId, currentId);

        // Três e não duas: a verificação de e-mail já abre sessão (§5.6), e os dois logins
        // seguintes acrescentam uma cada.
        assertThat(sessions.content()).hasSize(3);
        assertThat(sessions.content())
                .filteredOn(session -> session.id().equals(currentId))
                .singleElement()
                .satisfies(
                        session -> {
                            assertThat(session.current()).isTrue();
                            assertThat(session.ipAddress())
                                    .as("§9.2: o IP é parcialmente mascarado")
                                    .isEqualTo("203.***.***.10");
                        });
    }

    @Test
    @DisplayName("OWN-09/AC-001-38: revogar sessão de outro usuário devolve 404, não 403")
    void revokingForeignSessionMustReturnNotFound() {
        RegisterRequest ownerRequest = registerRequest("dono");
        UUID ownerId = registerAndVerify(ownerRequest);
        SessionOutcome ownerSession =
                authService.login(
                        new LoginRequest(ownerRequest.email(), VALID_PASSWORD), metadata());
        UUID foreignSessionId =
                refreshTokenService.identify(ownerSession.rawRefreshToken()).orElseThrow();

        RegisterRequest intruderRequest = registerRequest("intruso");
        UUID intruderId = registerAndVerify(intruderRequest);
        authenticateAs(intruderId);

        assertThatThrownBy(() -> sessionService.revokeOwn(intruderId, foreignSessionId))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(refreshTokenService.listActive(ownerId))
                .as("as sessões da vítima permanecem ativas — verificação de e-mail e login")
                .hasSize(2);
    }

    private record Fixture(
            String email, UUID userId, UUID secondTenantId, UUID secondMembershipId) {}

    /**
     * Usuário com vínculo ativo em duas organizações.
     *
     * <p>A segunda organização é criada por outro cadastro e o vínculo é acrescentado pela
     * interface pública de {@code MembershipService} — o convite (feature 002) ainda não existe, e
     * simular seu efeito é o mais próximo do estado real que o cenário permite.
     */
    private Fixture userWithTwoTenants() {
        RegisterRequest first = registerRequest("multi-a");
        UUID userId = registerAndVerify(first);
        RegisterRequest second = registerRequest("multi-b");
        RegisterResponse otherTenant = authService.register(second, metadata());

        UUID membershipId =
                runInTenant(
                        otherTenant.tenantId(),
                        userId,
                        Role.OWNER,
                        () -> {
                            Membership membership = new Membership();
                            membership.setTenantId(otherTenant.tenantId());
                            membership.setUserId(userId);
                            membership.setRole(Role.MEMBER);
                            membership.setStatus(MembershipStatus.ACTIVE);
                            membership.setAcceptedAt(FixedClockTestConfiguration.FIXED_INSTANT);
                            membership.setRoleChangedAt(FixedClockTestConfiguration.FIXED_INSTANT);
                            return membershipRepository.save(membership).getId();
                        });

        return new Fixture(first.email(), userId, otherTenant.tenantId(), membershipId);
    }

    private void suspendMembership(UUID tenantId, UUID userId, UUID membershipId) {
        runInTenant(
                tenantId,
                userId,
                Role.OWNER,
                () -> {
                    var membership = membershipRepository.findById(membershipId).orElseThrow();
                    membership.setStatus(MembershipStatus.SUSPENDED);
                    return membershipRepository.save(membership);
                });
    }

    private UUID registerAndVerify(RegisterRequest request) {
        RegisterResponse registered = authService.register(request, metadata());
        authService.verifyEmail(
                CapturedAuthEvents.tokenForEmail(request.email().toLowerCase()), metadata());
        return registered.userId();
    }
}
