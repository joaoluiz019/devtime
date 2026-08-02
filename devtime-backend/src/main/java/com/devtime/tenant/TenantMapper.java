package com.devtime.tenant;

import com.devtime.tenant.domain.Tenant;
import com.devtime.tenant.dto.TenantResponses.TenantResponse;
import com.devtime.tenant.dto.TenantSettings;
import com.devtime.tenant.dto.TenantViews.TenantState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link Tenant} para a resposta de users.md §6.1.
 *
 * <p>Escrito à mão porque {@code settings} não é cópia de campo: vem de {@code JSONB} já convertido
 * e com os padrões de §6.1.1 aplicados. BR-105: o mapper não acessa banco — a configuração chega
 * pronta de {@code TenantSettingsService}.
 */
@Component
@RequiredArgsConstructor
public class TenantMapper {

    public TenantResponse toResponse(Tenant tenant, TenantSettings settings) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getLegalName(),
                tenant.getDocumentNumber(),
                tenant.getEmail(),
                tenant.getPhone(),
                tenant.getTimezone(),
                tenant.getLocale(),
                tenant.getCurrency(),
                tenant.getLogoUrl(),
                tenant.getAddress(),
                TenantState.valueOf(tenant.getStatus().name()),
                tenant.getPlanCode(),
                settings,
                tenant.getVersion() == null ? 0L : tenant.getVersion());
    }
}
