package com.devtime.tenant;

import com.devtime.shared.tenancy.SessionValidationService;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import com.devtime.tenant.dto.TenantViews.SessionSnapshot;
import com.devtime.tenant.dto.TenantViews.TenantState;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link SessionValidationService} (T-001-14).
 *
 * <p>Uma única consulta por requisição, com organização e vínculo na mesma projeção. Duas consultas
 * dobrariam o custo fixo de toda a API para verificar dois enums.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionValidationServiceImpl implements SessionValidationService {

    private final TenantService tenantService;

    @Override
    public Decision validate(UUID tenantId, UUID userId, Instant tokenIssuedAt) {
        SessionSnapshot snapshot = tenantService.sessionSnapshot(tenantId, userId).orElse(null);
        if (snapshot == null) {
            // Sem vínculo: o token declara uma organização à qual o usuário não pertence (mais)
            // — indistinguível de vínculo removido, e tratado da mesma forma (RN-459).
            return Decision.MEMBERSHIP_INACTIVE;
        }
        // Ordem de permissions.md §4.1: organização (3) antes de vínculo (4). Cancelada bloqueia
        // tudo, inclusive para quem tem vínculo ativo.
        if (snapshot.tenantStatus() == TenantState.CANCELLED) {
            return Decision.TENANT_CANCELLED;
        }
        if (snapshot.membershipStatus() != MembershipState.ACTIVE) {
            return Decision.MEMBERSHIP_INACTIVE;
        }
        // TK-05 / IMP-04: alteração de papel invalida os tokens emitidos antes dela. Sem isto, um
        // ADMIN rebaixado manteria privilégios administrativos pelos 15 minutos de validade.
        if (tokenIssuedAt != null
                && snapshot.roleChangedAt() != null
                && tokenIssuedAt.isBefore(snapshot.roleChangedAt())) {
            return Decision.TOKEN_STALE;
        }
        if (snapshot.tenantStatus() == TenantState.SUSPENDED) {
            return Decision.TENANT_READ_ONLY;
        }
        return Decision.ALLOWED;
    }
}
