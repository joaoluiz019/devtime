package com.devtime.tenant.dto;

import com.devtime.shared.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Entradas de gestão de membros (users.md §7). */
public final class MemberRequests {

    private MemberRequests() {}

    /**
     * Convite (users.md §7.2).
     *
     * @param role {@code OWNER} exige que o requisitante também seja {@code OWNER} (nota ¹)
     * @param message texto opcional incluído no e-mail
     */
    @Schema(name = "InvitationRequest")
    public record InvitationRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull Role role,
            @Size(max = 500) String message) {}

    /**
     * Alteração de papel (users.md §7.3).
     *
     * @param version RN-004: precisa corresponder ao estado atual do vínculo
     */
    @Schema(name = "RoleUpdateRequest")
    public record RoleUpdateRequest(@NotNull Role role, @NotNull Long version) {}
}
