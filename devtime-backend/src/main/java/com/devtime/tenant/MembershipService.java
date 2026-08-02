package com.devtime.tenant;

import com.devtime.shared.security.Role;
import com.devtime.tenant.dto.TenantViews.MembershipView;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Interface pública mínima da feature {@code 002-users}, publicada por {@code 007-tickets}.
 *
 * <p>Cobre exclusivamente o que RN-304 (responsável do ticket) e RN-813 (menções) exigem: saber
 * quem é membro <b>ativo</b> do tenant da sessão. Convite, papel, suspensão e remoção pertencem a
 * {@code 002} e não são expostos aqui.
 *
 * <p>Todas as consultas são tenant-scoped pelo filtro automático (ART-022): um usuário ativo em
 * outro tenant é indistinguível de inexistente (ART-024).
 */
public interface MembershipService {

    /**
     * RN-304: o usuário possui membership {@code ACTIVE} no tenant da sessão.
     *
     * @return {@code false} para usuário inexistente, inativo, suspenso, removido ou de outro
     *     tenant — todas as causas produzem a mesma resposta, por ART-024
     */
    boolean isActiveMember(UUID userId);

    /** Identificadores dos membros ativos do tenant, em uma única consulta (RN-813). */
    Set<UUID> activeMemberIds();

    /**
     * RN-607: membros ativos do tenant com um dos papéis informados.
     *
     * <p>Interface pública para {@code 013-notifications}, que resolve destinatários por tipo de
     * evento — {@code OWNER} e {@code ADMIN} para contrato e período. O filtro por papel acontece
     * na consulta, e não em memória: o resolvedor não deve receber a lista de todos os membros para
     * descartar a maioria.
     *
     * @return conjunto possivelmente vazio; nunca {@code null} (ER-06)
     */
    Set<UUID> activeMemberIdsWithRoles(java.util.Collection<Role> roles);

    /**
     * INV-TEN-02: cria o vínculo {@code OWNER} {@code ACTIVE} do cadastro.
     *
     * <p>Nasce ativo, e não convidado: quem cadastra a organização é o próprio titular, e não há
     * convite a aceitar. É essa criação que satisfaz "todo tenant possui ao menos um OWNER".
     */
    UUID createOwner(UUID tenantId, UUID userId);

    /** Vínculo do usuário na organização indicada, em qualquer estado (RN-459). */
    Optional<MembershipView> findByTenantAndUser(UUID tenantId, UUID userId);

    /** Vínculos {@code ACTIVE} do usuário, em todas as organizações (INV-USR-04). */
    List<MembershipView> findActiveByUser(UUID userId);

    /**
     * §4.2 de state-machines.md: ativa os vínculos {@code INVITED} do usuário.
     *
     * <p>Executado dentro da transação da verificação de e-mail: um convite aceito por alguém que
     * ainda não confirmou o endereço só passa a valer quando a confirmação ocorre.
     *
     * @return quantidade de vínculos ativados
     */
    int activateInvitedFor(UUID userId);

    /**
     * RN-457: ativa um vínculo específico no aceite de convite.
     *
     * @throws com.devtime.shared.error.BusinessRuleException quando o vínculo não está {@code
     *     INVITED} — CP-09 proíbe reativar um {@code REMOVED}
     */
    void activate(UUID membershipId, Role role);

    /** users.md §7.1: membros da organização, com filtros de papel, situação e busca. */
    com.devtime.shared.pagination.PageResponse<
                    com.devtime.tenant.dto.MemberResponses.MemberResponse>
            search(
                    Role role,
                    com.devtime.tenant.dto.TenantViews.MembershipState status,
                    String search,
                    org.springframework.data.domain.Pageable pageable);

    /** users.md §7: detalhe do membro; vínculo de outro tenant responde 404 (ART-024). */
    com.devtime.tenant.dto.MemberResponses.MemberResponse getById(UUID membershipId);

    /**
     * users.md §7.3: altera o papel na ordem normativa de §6.1.
     *
     * <p>A invalidação dos access tokens do alvo (IMP-04) é consequência de {@code roleChangedAt},
     * comparado a cada requisição pelo {@code SessionValidationService} — não depende de o cliente
     * fazer nada.
     */
    com.devtime.tenant.dto.MemberResponses.MemberResponse changeRole(
            UUID membershipId, com.devtime.tenant.dto.MemberRequests.RoleUpdateRequest request);

    /** §4.3 de state-machines.md: {@code ACTIVE → SUSPENDED}; descarta o cronômetro (RN-460). */
    com.devtime.tenant.dto.MemberResponses.MemberResponse suspend(UUID membershipId);

    /** §4.3: {@code SUSPENDED → ACTIVE}. */
    com.devtime.tenant.dto.MemberResponses.MemberResponse reactivate(UUID membershipId);

    /**
     * users.md §7.4: remove o membro preservando registros (RN-458).
     *
     * @param reassignTicketsTo novo responsável pelos tickets abertos; nulo usa quem executa
     */
    com.devtime.tenant.dto.MemberResponses.MemberRemovalResponse remove(
            UUID membershipId, UUID reassignTicketsTo);
}
