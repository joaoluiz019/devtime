package com.devtime.tenant.dto;

import com.devtime.shared.security.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Visões de {@code Tenant} e {@code Membership} expostas na fronteira da feature.
 *
 * <p>Os enums são redeclarados aqui em vez de reexportados de {@code tenant.domain}: AR-02 proíbe
 * que {@code auth} dependa daquele pacote, e uma feature que precisa decidir entre {@code
 * DEVTIME-1201} (suspenso) e {@code DEVTIME-1202} (cancelado) precisa do valor, não de um booleano.
 */
public final class TenantViews {

    private TenantViews() {}

    /** Espelha {@code TenantStatus} (state-machines.md §4.1). */
    public enum TenantState {
        ACTIVE,
        /** RN-007: apenas leitura. */
        SUSPENDED,
        /** RN-008: nenhum acesso. */
        CANCELLED
    }

    /** Espelha {@code MembershipStatus} (state-machines.md §4.3). */
    public enum MembershipState {
        INVITED,
        ACTIVE,
        SUSPENDED,
        REMOVED
    }

    /**
     * Organização, como exibida na sessão.
     *
     * @param settings JSON de {@code entities.md} §6.1.1, repassado tal como persistido
     */
    public record TenantView(
            UUID id,
            String name,
            String slug,
            String timezone,
            String locale,
            String currency,
            String logoUrl,
            TenantState status,
            String planCode,
            String settings) {}

    /**
     * Vínculo do usuário com uma organização.
     *
     * @param roleChangedAt TK-05 / IMP-04: access tokens emitidos antes deste instante são
     *     rejeitados
     */
    public record MembershipView(
            UUID id,
            UUID tenantId,
            UUID userId,
            Role role,
            MembershipState status,
            Instant roleChangedAt,
            Instant acceptedAt,
            UUID invitedBy) {

        public boolean isActive() {
            return status == MembershipState.ACTIVE;
        }
    }

    /**
     * Opção de organização apresentada na seleção (spec 001 §23, {@code TenantOptionResponse}).
     *
     * <p>Achata {@code Membership} e {@code Tenant} em uma leitura só. CX-08 exige que o tenant
     * suspenso apareça <b>marcado</b> na lista, e não omitido: omitir faria o usuário concluir que
     * perdeu o acesso, quando na verdade a organização está em leitura.
     */
    public record TenantOption(
            UUID id, String name, String slug, Role role, String logoUrl, TenantState status) {}

    /**
     * Estado consultado a cada requisição autenticada (permissions.md §4.1, passos 3 e 4).
     *
     * <p>Uma única projeção com tenant e membership: são duas verificações, mas uma consulta. Duas
     * consultas por requisição dobrariam o custo fixo de toda a API para checar dois enums.
     */
    public record SessionSnapshot(
            TenantState tenantStatus, MembershipState membershipStatus, Instant roleChangedAt) {}
}
