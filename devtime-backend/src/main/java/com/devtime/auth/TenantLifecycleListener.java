package com.devtime.auth;

import com.devtime.tenant.event.TenantEvents.MemberRemovedEvent;
import com.devtime.tenant.event.TenantEvents.MemberSuspendedEvent;
import com.devtime.tenant.event.TenantEvents.TenantCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revogação de sessões disparada por {@code 002-users} (§15 daquela spec).
 *
 * <p>Vive em {@code auth} porque é aqui que o refresh token existe, e porque {@code auth} já
 * depende de {@code tenant} — o caminho inverso fecharia um ciclo (BR-008).
 *
 * <p>BR-183: {@code @EventListener}, e não {@code AFTER_COMMIT}. A revogação precisa ser
 * <b>atômica</b> com a mudança de estado: se a remoção do vínculo for confirmada mas a revogação
 * falhar, o membro removido continua com refresh token válido — exatamente o que RN-458 e RT-07
 * proíbem. Notificação e e-mail, que são efeito colateral, continuam pós-commit em outra classe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantLifecycleListener {

    private final RefreshTokenService refreshTokenService;

    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    void onMemberRemoved(MemberRemovedEvent event) {
        int revoked =
                refreshTokenService.revokeAllOfInTenant(event.targetUserId(), event.tenantId());
        log.warn("sessões revogadas por remoção de vínculo quantidade={}", revoked);
    }

    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    void onMemberSuspended(MemberSuspendedEvent event) {
        int revoked =
                refreshTokenService.revokeAllOfInTenant(event.targetUserId(), event.tenantId());
        log.warn("sessões revogadas por suspensão de vínculo quantidade={}", revoked);
    }

    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    void onTenantCancelled(TenantCancelledEvent event) {
        int revoked = refreshTokenService.revokeAllInTenant(event.tenantId());
        log.error("sessões revogadas por cancelamento da organização quantidade={}", revoked);
    }
}
