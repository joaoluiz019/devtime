package com.devtime.tenant;

import com.devtime.tenant.dto.TenantRequests.TenantCancelRequest;
import com.devtime.tenant.dto.TenantRequests.TenantSettingsRequest;
import com.devtime.tenant.dto.TenantRequests.TenantUpdateRequest;
import com.devtime.tenant.dto.TenantResponses.TenantCancelResponse;
import com.devtime.tenant.dto.TenantResponses.TenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organização da sessão (users.md §6).
 *
 * <p>Nenhuma rota recebe {@code tenantId}: a organização é sempre a da sessão (ART-021, BR-090).
 */
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Tag(name = "Organização", description = "Dados, configurações operacionais e cancelamento")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "Dados da organização")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Organização com settings tipado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — sem TENANT_VIEW")
    })
    public TenantResponse current() {
        return tenantService.currentDetail();
    }

    @PatchMapping
    @Operation(
            summary = "Atualiza os dados da organização",
            description = "O slug é imutável (RN-011) e não faz parte do contrato de entrada.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Organização atualizada"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2004 — conflito de versão"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2000 — fuso ou moeda inválidos")
    })
    public TenantResponse update(@Valid @RequestBody TenantUpdateRequest request) {
        return tenantService.update(request);
    }

    @PatchMapping("/settings")
    @Operation(
            summary = "Atualiza as configurações operacionais",
            description =
                    "Alterações valem apenas para registros futuros; nada é recalculado (ART-005).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configurações atualizadas"),
        @ApiResponse(
                responseCode = "422",
                description =
                        "DEVTIME-2020 — limiares de cronômetro inconsistentes;"
                                + " DEVTIME-2021 — arredondamento não suportado"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2004 — conflito de versão")
    })
    public TenantResponse updateSettings(@Valid @RequestBody TenantSettingsRequest request) {
        return tenantService.updateSettings(request);
    }

    @PostMapping("/cancel")
    @Operation(
            summary = "Cancela a organização",
            description =
                    "Exige senha e a confirmação exata CANCELAR (SG-04). Revoga as sessões e agenda"
                            + " a purga para 30 dias (RN-008).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Organização cancelada"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-1011 — senha incorreta"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2010 — período em fechamento"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1101 — apenas OWNER")
    })
    public TenantCancelResponse cancel(@Valid @RequestBody TenantCancelRequest request) {
        return tenantService.cancel(request);
    }
}
