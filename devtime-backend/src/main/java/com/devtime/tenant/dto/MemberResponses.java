package com.devtime.tenant.dto;

import com.devtime.shared.security.Role;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Saídas de gestão de membros (users.md §7). */
public final class MemberResponses {

    private MemberResponses() {}

    /** Pessoa por trás do vínculo, no formato de users.md §7.1. */
    @Schema(name = "MemberUser")
    public record MemberUserResponse(
            UUID id, String fullName, String displayName, String email, String avatarUrl) {}

    /**
     * Membro da organização (users.md §7.1).
     *
     * <p>O bloco {@code stats} de users.md §7.1 não é emitido: ele exige agregações por membro em
     * {@code 008} e {@code 009} que não possuem interface pública de contagem, e IDG-01 restringe
     * sua exibição a papéis com {@code WORKLOG_VIEW_ANY}. Emitir zeros seria pior — indicaria que o
     * membro não trabalhou.
     *
     * @param availableActions ações que o <b>requisitante</b> pode executar sobre este membro,
     *     calculadas a partir do papel de ambos (ME-06); é o que permite à UI ocultar botões que
     *     resultariam em {@code DEVTIME-1104} ou {@code DEVTIME-2456}
     */
    @Schema(name = "MemberResponse")
    public record MemberResponse(
            UUID id,
            MemberUserResponse user,
            Role role,
            MembershipState status,
            Instant invitedAt,
            Instant acceptedAt,
            List<String> availableActions,
            long version) {}

    /** Convite emitido (users.md §7.2). */
    @Schema(name = "MemberInvitationResponse")
    public record MemberInvitationResponse(
            UUID id,
            String email,
            Role role,
            MembershipState status,
            Instant invitedAt,
            Instant expiresAt) {}

    /**
     * Efeitos da remoção (users.md §7.4, spec §23).
     *
     * <p>Existe para tornar RN-458 visível: sem estes números, o usuário que remove um membro não
     * tem como saber que as 342 horas registradas continuam nos relatórios e no saldo.
     */
    @Schema(name = "MemberRemovalResponse")
    public record MemberRemovalResponse(
            MembershipState status,
            long workLogsPreserved,
            int ticketsReassigned,
            UUID reassignedTo,
            boolean activeTimerDiscarded) {}
}
