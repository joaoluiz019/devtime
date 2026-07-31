package com.devtime.auth;

import com.devtime.audit.AuditService;
import com.devtime.shared.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Trilha das ações de autenticação (spec 001 §18 e §28).
 *
 * <p><b>Limitação documentada.</b> {@code audit_logs.tenant_id} é {@code NOT NULL} (V006,
 * entities.md §6.20, database.md §7), e a tabela é indexada por tenant. Parte das ações da §18
 * ocorre <b>antes</b> de existir organização na sessão — login falho, "esqueci a senha" com e-mail
 * inexistente, renovação de token de pré-seleção — e portanto não tem tenant a informar. Para
 * essas, o registro é feito no log estruturado de segurança, que a §28 desta mesma spec e a §10 de
 * {@code security.md} já exigem com conteúdo definido.
 *
 * <p>O critério é único e explícito: <b>há tenant no contexto → {@code AuditLog} + log; não há →
 * apenas log</b>. Assim, as ações cujos critérios de aceite exigem trilha em banco — cadastro
 * (AC-001-01), seleção de organização (AC-001-05) e aceite de convite (AC-001-10) — sempre a
 * produzem, porque nas três o tenant é conhecido.
 *
 * <p>CP-11 / ART-084: nenhum método aqui registra e-mail em claro, senha, hash ou valor de token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthAuditRecorder {

    static final String ENTITY_USER = "User";
    static final String ENTITY_MEMBERSHIP = "Membership";
    static final String ENTITY_SESSION = "RefreshToken";

    private final AuditService auditService;
    private final TenantContext tenantContext;

    /**
     * Registra a ação na trilha quando houver tenant, e sempre no log estruturado.
     *
     * @param afterState apenas campos alterados; nunca dado sensível
     */
    public void record(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {
        if (tenantContext.currentTenantId().isPresent()) {
            auditService.record(action, entityType, entityId, beforeState, afterState);
        }
        log.info("evento de autenticação action={} entityId={}", action, entityId);
    }
}
