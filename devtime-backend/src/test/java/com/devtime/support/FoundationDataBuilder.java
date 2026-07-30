package com.devtime.support;

import com.devtime.shared.security.Role;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.Tenant;
import com.devtime.tenant.domain.TenantStatus;
import com.devtime.user.domain.User;
import com.devtime.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Construtores de dados de teste (BR-207: nunca SQL bruto).
 *
 * <p>Builders em vez de {@code INSERT} direto porque o objeto precisa passar pelo mesmo caminho de
 * persistência da produção — incluindo {@code AuditListener}, geração de UUIDv7 e atribuição de
 * tenant. Um {@code INSERT} de setup contornaria exatamente os mecanismos sob teste.
 */
public final class FoundationDataBuilder {

    private FoundationDataBuilder() {}

    public static Tenant tenant(String slug) {
        Tenant tenant = new Tenant();
        tenant.setName("Organização " + slug);
        tenant.setSlug(slug);
        tenant.setEmail("contato@" + slug + ".exemplo.com");
        tenant.setTimezone("America/Sao_Paulo");
        tenant.setLocale("pt-BR");
        tenant.setCurrency("BRL");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlanCode("FREE");
        tenant.setSettings("{}");
        return tenant;
    }

    public static User user(String email, Instant now) {
        User user = new User();
        user.setEmail(email);
        // Hash fictício: nenhum teste desta suíte autentica com senha, e um hash real só somaria
        // tempo de BCrypt à execução.
        user.setPasswordHash("$2a$04$0000000000000000000000000000000000000000000000000000");
        user.setFullName("Pessoa de Teste");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(now);
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts((short) 0);
        user.setPreferences("{}");
        return user;
    }

    public static Membership membership(UUID tenantId, UUID userId, Role role, Instant now) {
        Membership membership = new Membership();
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setRole(role);
        // INV-MEM-04: status ACTIVE exige acceptedAt preenchido.
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setAcceptedAt(now);
        membership.setRoleChangedAt(now);
        return membership;
    }
}
