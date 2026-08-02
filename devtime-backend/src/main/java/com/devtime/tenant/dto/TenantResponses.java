package com.devtime.tenant.dto;

import com.devtime.shared.persistence.Address;
import com.devtime.tenant.dto.TenantViews.TenantState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Saídas de organização (users.md §6). */
public final class TenantResponses {

    private TenantResponses() {}

    /**
     * Dados completos da organização (users.md §6.1).
     *
     * <p>O bloco {@code stats} de users.md §6.1 <b>não</b> é emitido nesta sprint: {@code
     * activeClients}, {@code activeContracts} e {@code storageUsedBytes} exigiriam contadores
     * públicos em {@code 003}, {@code 004} e {@code 015}, que não existem. Emitir o objeto
     * parcialmente preenchido seria pior que omiti-lo — zeros seriam lidos como "nenhum cliente
     * cadastrado", a mesma razão pela qual {@code 005-categories} omitiu o bloco {@code usage}.
     *
     * @param slug somente leitura; imutável por RN-011
     * @param settings configuração tipada com os padrões de §6.1.1 já aplicados
     */
    @Schema(name = "TenantResponse")
    public record TenantResponse(
            UUID id,
            String name,
            String slug,
            String legalName,
            String documentNumber,
            String email,
            String phone,
            String timezone,
            String locale,
            String currency,
            String logoUrl,
            Address address,
            TenantState status,
            String planCode,
            TenantSettings settings,
            long version) {}

    /**
     * Resultado do cancelamento (users.md §6.3).
     *
     * @param dataRetainedUntil RN-008: 30 dias de retenção antes da purga
     * @param exportAvailableUntil coincide com a retenção; a exportação continua permitida (RN-007)
     */
    @Schema(name = "TenantCancelResponse")
    public record TenantCancelResponse(
            TenantState status, Instant dataRetainedUntil, Instant exportAvailableUntil) {}
}
