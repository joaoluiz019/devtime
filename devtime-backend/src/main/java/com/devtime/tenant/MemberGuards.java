package com.devtime.tenant;

import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import com.devtime.tenant.domain.TenantExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * As três guardas de §6.1, na ordem normativa (spec 002 §22.3).
 *
 * <p>Reunidas em uma classe porque a <b>ordem entre elas</b> é a regra, e distribuí-las em três
 * arquivos esconderia justamente isso. A ordem não é preferência de implementação (BR-062):
 *
 * <ol>
 *   <li>auto-alteração (RN-456) antes de hierarquia — um OWNER tentando se rebaixar precisa ler
 *       "não é possível alterar o próprio papel", não uma mensagem sobre hierarquia que não explica
 *       nada;
 *   <li>hierarquia (nota ¹) antes do último OWNER — é verificação em memória;
 *   <li>último OWNER (RN-455) por último — é a mais cara, exige contagem com lock no banco.
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberGuards {

    private final MembershipRepository repository;
    private final TenantContext tenantContext;

    /** RN-456 / OWN-06: ninguém altera o próprio papel, nem sendo OWNER. */
    public void assertNotSelf(UUID targetUserId) {
        if (tenantContext.requireUserId().equals(targetUserId)) {
            log.warn("tentativa de auto-alteração de papel bloqueada");
            throw TenantExceptions.selfRoleChange();
        }
    }

    /**
     * Nota ¹ de permissions.md §7: {@code ADMIN} não age sobre {@code OWNER} nem promove a {@code
     * OWNER}.
     *
     * @param targetRole papel atual do alvo
     * @param newRole papel pretendido; nulo em suspensão e remoção, onde não há promoção a avaliar
     */
    public void assertHierarchyAllowed(Role targetRole, Role newRole) {
        Role actorRole = tenantContext.currentRole().orElse(null);
        if (actorRole == Role.OWNER) {
            return; // OWNER age sobre qualquer papel.
        }
        if (targetRole == Role.OWNER || newRole == Role.OWNER) {
            throw TenantExceptions.adminOverOwner(); // DEVTIME-1104
        }
    }

    /**
     * RN-455 / INV-MEM-02: a operação não pode deixar o tenant sem OWNER ativo.
     *
     * <p>A contagem usa <b>lock pessimista</b> sobre as linhas de OWNER ativo (CX-02). Sem ele,
     * dois ADMINs rebaixando OWNERs distintos simultaneamente leriam "existem 2 OWNERs" e ambos
     * teriam sucesso — deixando o tenant sem nenhum, estado do qual não há saída pela própria API.
     *
     * @param affected vínculo que deixará de ser OWNER ativo
     */
    public void assertNotLastOwner(Membership affected) {
        if (affected.getRole() != Role.OWNER || affected.getStatus() != MembershipStatus.ACTIVE) {
            return; // Não é OWNER ativo: a operação não altera a contagem.
        }
        long remaining =
                repository.lockActiveOwners().stream()
                        .filter(owner -> !owner.getId().equals(affected.getId()))
                        .count();
        if (remaining == 0) {
            log.warn("operação bloqueada por deixar o tenant sem OWNER ativo");
            throw TenantExceptions.lastOwner(); // DEVTIME-2455
        }
    }
}
