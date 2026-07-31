package com.devtime.tenant.dto;

import com.devtime.shared.security.Role;
import java.util.UUID;

/** Comandos de escrita de organização e vínculo, emitidos por {@code 001-authentication}. */
public final class TenantCommands {

    private TenantCommands() {}

    /**
     * Provisionamento de organização no cadastro (spec 001 §7, passo 3).
     *
     * @param name 2–120 caracteres; o {@code slug} é derivado dele com resolução de colisão (CX-03)
     * @param contactEmail e-mail de contato — entities.md §6.1 o exige; no cadastro é o do próprio
     *     titular, que é quem responde pela organização recém-criada
     * @param timezone ID IANA validado (INV-TEN-03)
     */
    public record NewTenant(String name, String contactEmail, String timezone) {}

    /** Criação de vínculo. No cadastro, sempre {@code OWNER} {@code ACTIVE} (INV-TEN-02). */
    public record NewMembership(UUID tenantId, UUID userId, Role role, boolean active) {}
}
