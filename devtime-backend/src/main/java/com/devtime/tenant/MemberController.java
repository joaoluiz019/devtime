package com.devtime.tenant;

import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.security.Role;
import com.devtime.tenant.dto.MemberRequests.InvitationRequest;
import com.devtime.tenant.dto.MemberRequests.RoleUpdateRequest;
import com.devtime.tenant.dto.MemberResponses.MemberInvitationResponse;
import com.devtime.tenant.dto.MemberResponses.MemberRemovalResponse;
import com.devtime.tenant.dto.MemberResponses.MemberResponse;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Membros da organização (users.md §7).
 *
 * <p>BR-089: suspensão e reativação são ações de máquina de estado e usam {@code POST
 * /{id}/{ação}}, nunca {@code PATCH} sobre o campo {@code status}.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Membros", description = "Vínculos, convites e papéis (RN-455 a RN-460)")
public class MemberController {

    private final MembershipService membershipService;
    private final InvitationService invitationService;

    @GetMapping
    @Operation(summary = "Lista os membros da organização")
    @ApiResponse(responseCode = "200", description = "Página de membros")
    public PageResponse<MemberResponse> search(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) MembershipState status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return membershipService.search(role, status, search, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um membro")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Membro encontrado"),
        @ApiResponse(responseCode = "404", description = "DEVTIME-2002 — de outro tenant")
    })
    public MemberResponse getById(@PathVariable UUID id) {
        return membershipService.getById(id);
    }

    @GetMapping("/invitations")
    @Operation(summary = "Lista os convites pendentes")
    @ApiResponse(responseCode = "200", description = "Convites em INVITED")
    public List<MemberInvitationResponse> listInvitations() {
        return invitationService.listPending();
    }

    @PostMapping("/invitations")
    @Operation(
            summary = "Convida um membro",
            description =
                    "Convite válido por 7 dias (RN-457). O aceite é público e pertence a 001.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Convite emitido"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2459 — já é membro ou convidado"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1104 — ADMIN concedendo OWNER")
    })
    public ResponseEntity<MemberInvitationResponse> invite(
            @Valid @RequestBody InvitationRequest request) {
        MemberInvitationResponse created = invitationService.invite(request);
        // BR-088: 201 sempre acompanha Location.
        return ResponseEntity.created(URI.create("/api/v1/members/" + created.id())).body(created);
    }

    @PostMapping("/invitations/{id}/resend")
    @Operation(
            summary = "Reenvia um convite",
            description = "RN-457: emite novo token e invalida o anterior.")
    @ApiResponse(responseCode = "202", description = "Novo convite emitido")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MemberInvitationResponse resend(@PathVariable UUID id) {
        return invitationService.resend(id);
    }

    @DeleteMapping("/invitations/{id}")
    @Operation(summary = "Revoga um convite pendente")
    @ApiResponse(responseCode = "204", description = "Convite revogado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable UUID id) {
        invitationService.revoke(id);
    }

    @PatchMapping("/{id}/role")
    @Operation(
            summary = "Altera o papel de um membro",
            description = "Invalida imediatamente os access tokens do alvo neste tenant (IMP-04).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Papel alterado"),
        @ApiResponse(
                responseCode = "409",
                description = "DEVTIME-2455 — deixaria o tenant sem OWNER"),
        @ApiResponse(
                responseCode = "403",
                description = "DEVTIME-2456 — próprio papel;" + " DEVTIME-1104 — ADMIN sobre OWNER")
    })
    public MemberResponse changeRole(
            @PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest request) {
        return membershipService.changeRole(id, request);
    }

    @PostMapping("/{id}/suspend")
    @Operation(
            summary = "Suspende um membro",
            description = "Descarta o cronômetro ativo (RN-460).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Membro suspenso"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2455 — último OWNER")
    })
    public MemberResponse suspend(@PathVariable UUID id) {
        return membershipService.suspend(id);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reativa um membro suspenso")
    @ApiResponse(responseCode = "200", description = "Membro reativado")
    public MemberResponse reactivate(@PathVariable UUID id) {
        return membershipService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Remove um membro",
            description =
                    "Preserva registros de horas, tickets e comentários (RN-458); reatribui tickets"
                            + " abertos e descarta o cronômetro (RN-460).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Membro removido, com os efeitos aplicados"),
        @ApiResponse(responseCode = "409", description = "DEVTIME-2455 — último OWNER"),
        @ApiResponse(responseCode = "403", description = "DEVTIME-1104 — ADMIN removendo OWNER")
    })
    public MemberRemovalResponse remove(
            @PathVariable UUID id, @RequestParam(required = false) UUID reassignTicketsTo) {
        return membershipService.remove(id, reassignTicketsTo);
    }
}
