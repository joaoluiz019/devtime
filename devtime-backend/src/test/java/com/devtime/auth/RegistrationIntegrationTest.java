package com.devtime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.auth.dto.AuthResponses.RegisterResponse;
import com.devtime.category.CategoryRepository;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.TenantStatus;
import com.devtime.user.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cadastro (TS-001-06, AC-001-01, AC-001-12, AC-001-24, AC-001-25).
 *
 * <p>O foco é a <b>atomicidade</b>: usuário, organização, vínculo, categorias e token de
 * verificação existem juntos ou não existem (CE-01).
 */
class RegistrationIntegrationTest extends AuthTestSupport {

    @Autowired private CategoryRepository categoryRepository;

    @Test
    @DisplayName("RN-452/RN-501: o cadastro cria conta, organização, vínculo OWNER e 9 categorias")
    void registrationMustCreateEverythingAtomically() {
        RegisterRequest request = registerRequest("atomico");

        RegisterResponse response = authService.register(request, metadata());

        assertThat(response.status()).isEqualTo(UserStatus.PENDING_ACTIVATION.name());
        assertThat(response.email()).isEqualTo(request.email().toLowerCase());

        var tenant = tenantRepository.findById(response.tenantId()).orElseThrow();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getSlug()).startsWith("rafael-mendes-dev");

        var user = userRepository.findById(response.userId()).orElseThrow();
        assertThat(user.getStatus())
                .as("CP-08: a conta nasce pendente; o token só é emitido após a verificação")
                .isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.getEmailVerifiedAt()).isNull();

        var membership =
                runInTenant(
                        response.tenantId(),
                        response.userId(),
                        () -> membershipRepository.findByUserId(response.userId()).orElseThrow());
        assertThat(membership.getRole()).isEqualTo(Role.OWNER); // INV-TEN-02
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getAcceptedAt()).as("INV-MEM-04").isNotNull();

        long categories =
                runInTenant(response.tenantId(), response.userId(), categoryRepository::countAll);
        assertThat(categories).as("RN-501: 9 categorias padrão").isEqualTo(9);

        assertThat(
                        verificationTokenRepository.findAll().stream()
                                .filter(token -> token.getUserId().equals(response.userId()))
                                .filter(
                                        token ->
                                                token.getType()
                                                        == VerificationTokenType.EMAIL_VERIFICATION)
                                .toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-001-01: a resposta do cadastro não contém nenhum token de acesso")
    void registrationMustNotIssueAccessToken() {
        RegisterResponse response = authService.register(registerRequest("sem-token"), metadata());

        assertThat(response.toString())
                .as("CP-08: verificar o e-mail é pré-requisito para autenticar")
                .doesNotContain("eyJ");
    }

    @Test
    @DisplayName("RN-452: e-mail já cadastrado devolve DEVTIME-2452 sem criar nada")
    void duplicateEmailMustBeRejected() {
        RegisterRequest first = registerRequest("duplicado");
        authService.register(first, metadata());
        long tenantsBefore = tenantRepository.count();

        RegisterRequest second =
                new RegisterRequest(
                        first.email(),
                        VALID_PASSWORD,
                        "Outra Pessoa",
                        "Outra Organização",
                        "America/Sao_Paulo",
                        true);

        assertThatThrownBy(() -> authService.register(second, metadata()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);

        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore);
    }

    @Test
    @DisplayName(
            "CX-01/AC-001-24: e-mail com maiúsculas e espaços é normalizado antes da unicidade")
    void emailMustBeNormalizedBeforeUniquenessCheck() {
        RegisterRequest first = registerRequest("normalizado");
        authService.register(first, metadata());

        RegisterRequest disguised =
                new RegisterRequest(
                        "  " + first.email().toUpperCase() + "  ",
                        VALID_PASSWORD,
                        "Rafael Mendes",
                        "Rafael Mendes Dev",
                        "America/Sao_Paulo",
                        true);

        assertThatThrownBy(() -> authService.register(disguised, metadata()))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("RN-451: senha fora da política devolve DEVTIME-2451 e nada é criado")
    void weakPasswordMustBeRejectedBeforeAnyWrite() {
        long usersBefore = userRepository.count();
        RegisterRequest weak =
                new RegisterRequest(
                        "fraca-" + UUID.randomUUID() + "@exemplo.com",
                        "senha123",
                        "Rafael Mendes",
                        "Rafael Mendes Dev",
                        "America/Sao_Paulo",
                        true);

        assertThatThrownBy(() -> authService.register(weak, metadata()))
                .extracting(thrown -> ((BusinessRuleException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("CX-03/AC-001-25: colisão de slug resolve com sufixo, sem falhar o cadastro")
    void slugCollisionMustResolveWithSuffix() {
        RegisterResponse first = authService.register(registerRequest("slug-a"), metadata());
        RegisterResponse second = authService.register(registerRequest("slug-b"), metadata());

        String firstSlug = tenantRepository.findById(first.tenantId()).orElseThrow().getSlug();
        String secondSlug = tenantRepository.findById(second.tenantId()).orElseThrow().getSlug();

        assertThat(List.of(firstSlug, secondSlug)).doesNotHaveDuplicates();
        assertThat(secondSlug).startsWith("rafael-mendes-dev");
    }

    @Test
    @DisplayName("O contexto de tenant não vaza do cadastro para a operação seguinte")
    void tenantContextMustNotLeakAfterRegistration() {
        authService.register(registerRequest("contexto"), metadata());

        assertThat(tenantContext.currentTenantId())
                .as("uma thread do pool herdaria o tenant recém-criado na próxima requisição")
                .isEmpty();
    }

    /**
     * Executa uma leitura no contexto do tenant indicado, como faria uma requisição autenticada.
     */
    private <T> T runInTenant(UUID tenantId, UUID userId, java.util.function.Supplier<T> action) {
        tenantContext.set(
                new TenantSession(
                        userId,
                        tenantId,
                        null,
                        Role.OWNER,
                        RolePermissions.of(Role.OWNER),
                        "America/Sao_Paulo"));
        try {
            return transactionTemplate.execute(status -> action.get());
        } finally {
            tenantContext.clear();
        }
    }
}
