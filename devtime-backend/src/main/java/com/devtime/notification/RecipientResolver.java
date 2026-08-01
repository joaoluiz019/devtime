package com.devtime.notification;

import com.devtime.shared.security.Role;
import com.devtime.tenant.MembershipService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolução de destinatários por tipo de evento (RN-607).
 *
 * <p>Três regras, e nada além delas:
 *
 * <ul>
 *   <li><b>Contrato e período</b> → {@code OWNER} e {@code ADMIN} ativos. São quem responde
 *       comercialmente pelo tenant; um alerta de saldo estourado é decisão financeira.
 *   <li><b>Ticket</b> → o responsável. Quem precisa agir é quem carrega o trabalho.
 *   <li><b>Cronômetro</b> → o próprio dono. OWN-05 é a regra de propriedade mais restritiva do
 *       sistema, e a notificação a acompanha: nem {@code OWNER} é avisado do cronômetro alheio.
 * </ul>
 *
 * <p><b>NT-05: o autor da ação nunca é destinatário</b> (CE-N-05). Ninguém precisa ser avisado do
 * que acabou de fazer, e uma notificação assim ensina o usuário a ignorá-las. Por isso todo método
 * aceita o ator a excluir.
 *
 * <p>FA-05: um tenant sem {@code OWNER}/{@code ADMIN} ativo produz conjunto vazio e <b>nenhum
 * erro</b>. A situação é impossível por INV-TEN-02, e tratá-la como falha transformaria uma
 * anomalia de dados em interrupção do fluxo que a detectou.
 */
@Component
@RequiredArgsConstructor
public class RecipientResolver {

    /** RN-607: papéis que respondem por contrato e período. */
    private static final Set<Role> BILLING_ROLES = Set.of(Role.OWNER, Role.ADMIN);

    private final MembershipService membershipService;

    /** RN-607: destinatários de eventos de contrato e de período. */
    public Set<UUID> forContractEvents(UUID actorToExclude) {
        return excluding(membershipService.activeMemberIdsWithRoles(BILLING_ROLES), actorToExclude);
    }

    /**
     * RN-607: destinatário de evento de ticket.
     *
     * <p>Vazio quando o ticket não tem responsável — não existe a quem avisar — ou quando o
     * responsável é o próprio autor da ação (NT-05).
     */
    public Set<UUID> forTicketEvent(UUID assigneeId, UUID actorToExclude) {
        if (assigneeId == null || !membershipService.isActiveMember(assigneeId)) {
            return Set.of();
        }
        return excluding(Set.of(assigneeId), actorToExclude);
    }

    /**
     * RN-607 / OWN-05: destinatário de evento de cronômetro é sempre o dono.
     *
     * <p>FA-20 / CX-21: no encerramento forçado, quem recebe é o <b>dono</b>, não o administrador
     * que encerrou — e é justamente por isso que o ator é excluído.
     */
    public Set<UUID> forTimerEvent(UUID ownerId, UUID actorToExclude) {
        if (ownerId == null) {
            return Set.of();
        }
        return excluding(Set.of(ownerId), actorToExclude);
    }

    /**
     * Destinatários explícitos, filtrados por membership ativo.
     *
     * <p>CX-23: uma menção a membro inativo não gera notificação — RN-813 já filtra na origem, e
     * este filtro é a segunda barreira.
     */
    public Set<UUID> forExplicitRecipients(Set<UUID> candidates, UUID actorToExclude) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        Set<UUID> active = new LinkedHashSet<>();
        candidates.stream().filter(membershipService::isActiveMember).forEach(active::add);
        return excluding(active, actorToExclude);
    }

    /**
     * CE-N-04: o mesmo usuário em dois papéis recebe uma única notificação — o conjunto é único.
     */
    private Set<UUID> excluding(Set<UUID> recipients, UUID actorToExclude) {
        if (recipients.isEmpty()) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>(recipients);
        if (actorToExclude != null) {
            result.remove(actorToExclude); // NT-05
        }
        return Set.copyOf(result);
    }
}
