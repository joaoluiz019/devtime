package com.devtime.auth;

import com.devtime.auth.dto.AuthRequests.RegisterRequest;
import java.util.UUID;

/**
 * Provisionamento atômico de conta e organização (spec 001 §7, T-001-19).
 *
 * <p>CE-01 / AC-001-01 / TS-001-06: usuário, organização, vínculo {@code OWNER}, as 9 categorias
 * padrão (RN-501) e o token de verificação são criados em uma <b>única transação</b>. Falha em
 * qualquer etapa — inclusive no seed — não deixa registro algum.
 */
public interface TenantProvisioningService {

    /**
     * @param rawVerificationToken valor bruto do token de verificação; existe apenas neste retorno
     *     e é entregue ao consumidor de {@code UserRegisteredEvent} após o commit
     */
    record ProvisionedAccount(
            UUID userId, UUID tenantId, UUID membershipId, String rawVerificationToken) {}

    /**
     * @param normalizedEmail e-mail já normalizado (RN-452, AU-03)
     */
    ProvisionedAccount provision(RegisterRequest request, String normalizedEmail);
}
