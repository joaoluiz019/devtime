package com.devtime.support;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint existente apenas na suíte de testes.
 *
 * <p>A fundação técnica não expõe nenhum endpoint de negócio — é exatamente o que a sprint
 * determina. Mas o comportamento da borda HTTP (allowlist de ART-085, propagação do {@code
 * TenantContext} a partir do JWT e formato RFC 7807) só é verificável através de uma rota real.
 * Este controller existe para isso e <b>não</b> faz parte do artefato de produção.
 */
@RestController
@RequestMapping("/api/v1/test-probe")
@RequiredArgsConstructor
public class ProbeController {

    private final TenantContext tenantContext;

    /** Devolve o que o TenantContextFilter derivou das claims do token. */
    @GetMapping("/session")
    Map<String, Object> session() {
        return Map.of(
                "userId", tenantContext.requireUserId().toString(),
                "tenantId", tenantContext.requireTenantId().toString(),
                "role", tenantContext.currentRole().map(Enum::name).orElse(""),
                "permissionCount", tenantContext.currentPermissions().size());
    }

    /** Verifica que um tenantId enviado pelo cliente é ignorado (ART-021, TI-01). */
    @GetMapping("/session-ignoring-request-tenant")
    Map<String, Object> sessionIgnoringRequestTenant(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
                    String tenantId) {
        return Map.of("effectiveTenantId", tenantContext.requireTenantId().toString());
    }

    @GetMapping("/not-found")
    Map<String, Object> notFound() {
        throw EntityNotFoundException.of(ProbeController.class, UUID.randomUUID());
    }

    @GetMapping("/version-conflict")
    Map<String, Object> versionConflict() {
        throw BusinessRuleException.versionConflict("Membership", 3L);
    }

    @GetMapping("/unexpected")
    Map<String, Object> unexpected() {
        throw new IllegalArgumentException("detalhe interno que não deve chegar ao cliente");
    }

    @PostMapping("/validated")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> validated(@Valid @RequestBody ProbeRequest request) {
        return Map.of("name", request.name());
    }

    /** DT-01: todo DTO é um {@code record} imutável. */
    public record ProbeRequest(@NotBlank @Size(min = 3, max = 20) String name) {}
}
