package com.devtime.auth;

import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import com.devtime.category.CategoryService;
import com.devtime.shared.persistence.UuidGenerator;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.tenant.MembershipService;
import com.devtime.tenant.TenantService;
import com.devtime.tenant.dto.TenantCommands.NewTenant;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserCommands.NewAccount;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Implementação de {@link TenantProvisioningService}. */
@Service
public class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private final TransactionTemplate transactionTemplate;
    private final TenantContext tenantContext;
    private final TenantService tenantService;
    private final UserAccountService userAccountService;
    private final MembershipService membershipService;
    private final CategoryService categoryService;
    private final VerificationTokenService verificationTokenService;
    private final AuthAuditRecorder auditRecorder;

    /**
     * Constrói um {@link TransactionTemplate} próprio, com {@code REQUIRES_NEW}.
     *
     * <p>Não é o template compartilhado do contexto por duas razões. Primeiro, o chamador é um
     * serviço {@code readOnly = true}: participar da transação dele faria o PostgreSQL recusar todo
     * {@code INSERT} deste provisionamento. Segundo, a transação precisa <b>começar aqui</b>,
     * depois de o {@code TenantContext} estar posto — é na abertura que {@code
     * TenantAwareTransactionManager} ativa o filtro de tenant, e sem ele o seed de categorias
     * enxergaria o catálogo de todos os tenants.
     */
    public TenantProvisioningServiceImpl(
            PlatformTransactionManager transactionManager,
            TenantContext tenantContext,
            TenantService tenantService,
            UserAccountService userAccountService,
            MembershipService membershipService,
            CategoryService categoryService,
            VerificationTokenService verificationTokenService,
            AuthAuditRecorder auditRecorder) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenantContext = tenantContext;
        this.tenantService = tenantService;
        this.userAccountService = userAccountService;
        this.membershipService = membershipService;
        this.categoryService = categoryService;
        this.verificationTokenService = verificationTokenService;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public ProvisionedAccount provision(RegisterRequest request, String normalizedEmail) {
        // Os identificadores nascem aqui, fora da transação, e não é conveniência: o filtro de
        // tenant é ativado na abertura da transação (TenantAwareTransactionManager), portanto o
        // contexto — e com ele o tenantId — precisa existir antes dela. Sem isso, o seed de
        // categorias contaria as categorias de todos os tenants e concluiria que já existem
        // (CategoryService.seedDefaults é idempotente por contagem), deixando o novo tenant sem
        // catálogo e violando RN-501. ART-010 já prevê o UUIDv7 gerado na aplicação.
        UUID tenantId = UuidGenerator.newId();
        UUID userId = UuidGenerator.newId();

        Optional<TenantSession> previous = tenantContext.session();
        tenantContext.set(sessionFor(userId, tenantId, request.resolvedTimezone()));
        try {
            return transactionTemplate.execute(
                    status -> provisionInTransaction(request, normalizedEmail, tenantId, userId));
        } finally {
            // Restaura o estado anterior. Um cadastro nunca deve deixar o contexto apontando para o
            // tenant recém-criado depois de retornar — a próxima operação da mesma thread o
            // herdaria.
            previous.ifPresentOrElse(tenantContext::set, tenantContext::clear);
        }
    }

    private ProvisionedAccount provisionInTransaction(
            RegisterRequest request, String normalizedEmail, UUID tenantId, UUID userId) {
        tenantService.provision(
                tenantId,
                new NewTenant(
                        request.resolvedTenantName(), normalizedEmail, request.resolvedTimezone()));

        UUID createdUserId =
                userAccountService.create(
                        new NewAccount(
                                normalizedEmail,
                                request.password(),
                                request.fullName(),
                                request.resolvedTimezone()));

        // INV-TEN-02: o tenant nasce com um OWNER ativo, na mesma transação.
        UUID membershipId = membershipService.createOwner(tenantId, createdUserId);

        // RN-501: as 9 categorias padrão. Dentro da transação por exigência da §15 do spec — um
        // tenant sem categorias impede o primeiro registro de horas (RN-104).
        categoryService.seedDefaults();

        var token =
                verificationTokenService.issue(
                        createdUserId, null, VerificationTokenType.EMAIL_VERIFICATION);

        // §18: USER_REGISTERED com status e sem o e-mail em claro (CP-11).
        auditRecorder.record(
                "USER_REGISTERED",
                AuthAuditRecorder.ENTITY_USER,
                createdUserId,
                Map.of(),
                Map.of("status", "PENDING_ACTIVATION", "tenantId", tenantId.toString()));

        return new ProvisionedAccount(createdUserId, tenantId, membershipId, token.rawToken());
    }

    /**
     * Sessão temporária do cadastro.
     *
     * <p>O {@code membershipId} ainda não existe neste instante; permanece nulo, o que é aceito por
     * {@code TenantSession} e não é usado por nenhum caminho do provisionamento. O papel é {@code
     * OWNER} porque é o que o cadastro cria — usar outro faria o {@code seedDefaults} ser barrado
     * por falta de permissão.
     */
    private TenantSession sessionFor(UUID userId, UUID tenantId, String timezone) {
        return new TenantSession(
                userId, tenantId, null, Role.OWNER, RolePermissions.of(Role.OWNER), timezone);
    }
}
