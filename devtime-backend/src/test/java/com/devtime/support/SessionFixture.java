package com.devtime.support;

import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.tenant.MembershipRepository;
import com.devtime.tenant.TenantRepository;
import com.devtime.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cria organização, usuário e vínculo reais para testes que emitem access token.
 *
 * <p>Passou a ser necessário com T-001-14: o {@code TenantContextFilter} verifica situação da
 * organização e do vínculo a cada requisição (passos 3 e 4 de {@code permissions.md} §4.1). Um
 * token com identificadores sintéticos, que antes atravessava a cadeia, agora é recusado com {@code
 * 403 DEVTIME-1102} — e deve mesmo ser: é exatamente o caso do membro removido cujo token ainda não
 * expirou (CE-AU-07).
 */
@Component
public class SessionFixture {

    /** Sessão utilizável: os três identificadores existem e o vínculo está ativo. */
    public record Session(UUID tenantId, UUID userId, UUID membershipId) {}

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final TenantContext tenantContext;
    private final TransactionTemplate transactionTemplate;

    public SessionFixture(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            MembershipRepository membershipRepository,
            TenantContext tenantContext,
            TransactionTemplate transactionTemplate) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantContext = tenantContext;
        this.transactionTemplate = transactionTemplate;
    }

    public Session create(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantContext.clear();

        UUID tenantId =
                transactionTemplate.execute(
                        status ->
                                tenantRepository
                                        .save(FoundationDataBuilder.tenant("fx-" + suffix))
                                        .getId());
        UUID userId =
                transactionTemplate.execute(
                        status ->
                                userRepository
                                        .save(
                                                FoundationDataBuilder.user(
                                                        "fx-" + suffix + "@exemplo.com",
                                                        FixedClockTestConfiguration.FIXED_INSTANT))
                                        .getId());

        tenantContext.set(
                new TenantSession(
                        userId,
                        tenantId,
                        null,
                        role,
                        RolePermissions.of(role),
                        "America/Sao_Paulo"));
        try {
            UUID membershipId =
                    transactionTemplate.execute(
                            status ->
                                    membershipRepository
                                            .save(
                                                    FoundationDataBuilder.membership(
                                                            tenantId,
                                                            userId,
                                                            role,
                                                            FixedClockTestConfiguration
                                                                    .FIXED_INSTANT))
                                            .getId());
            return new Session(tenantId, userId, membershipId);
        } finally {
            tenantContext.clear();
        }
    }
}
