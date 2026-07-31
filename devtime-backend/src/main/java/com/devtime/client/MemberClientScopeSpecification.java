package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Escopo de dados de {@code MEMBER} sobre clientes (permissions.md §9, nota ²).
 *
 * <p>IMP-02 / AZ-04: o escopo é aplicado <b>na consulta</b>, não filtrando o resultado depois —
 * filtrar depois vazaria a contagem total e permitiria inferir a carteira de clientes por
 * paginação.
 *
 * <p>A definição operacional de "cliente vinculado" (§9) é:
 *
 * <pre>
 * contrato C é visível para o membro M se existe work_log W com W.contract_id = C.id e
 * W.user_id = M.id, OU existe ticket T com T.contract_id = C.id e (T.assignee_id = M.id OU
 * T.reporter_id = M.id); um cliente é visível quando possui algum contrato visível
 * </pre>
 *
 * <p>Os vínculos chegam pelas implementações de {@link MemberScopeSource}, contribuídas pelas
 * features que <b>possuem</b> a informação: os tickets a partir desta sprint, os work logs com
 * {@code 008}. Sem nenhuma fonte, o conjunto é vazio e o escopo permanece <b>fechado</b> — que é o
 * comportamento exigido por ART-085 e o que valeu enquanto {@code tickets} não existia. Conceder
 * visão total nesse intervalo abriria a carteira de clientes ao papel que a nota ² restringe.
 */
@Component
@RequiredArgsConstructor
public class MemberClientScopeSpecification {

    private final TenantContext tenantContext;

    /** Injetada como lista: acrescentar uma fonte não exige tocar nesta classe. */
    private final List<MemberScopeSource> scopeSources;

    /**
     * @return especificação sempre verdadeira para papéis de visão total; restrição por
     *     identificador para {@code MEMBER}
     */
    public Specification<Client> forCurrentRole() {
        if (!isRestricted()) {
            return (root, query, builder) -> builder.conjunction();
        }
        Set<UUID> visible = linkedClientIds();
        if (visible.isEmpty()) {
            return (root, query, builder) -> builder.disjunction();
        }
        return (root, query, builder) -> root.get("id").in(visible);
    }

    /** CE-P-05: acesso direto por id fora do escopo resulta em {@code 404}, nunca {@code 403}. */
    public boolean isWithinScope(UUID clientId) {
        return !isRestricted() || linkedClientIds().contains(clientId);
    }

    private boolean isRestricted() {
        return tenantContext.currentRole().filter(Role.MEMBER::equals).isPresent();
    }

    private Set<UUID> linkedClientIds() {
        UUID userId = tenantContext.currentUserId().orElse(null);
        if (userId == null) {
            return Set.of();
        }
        return scopeSources.stream()
                .flatMap(source -> source.linkedClientIdsOf(userId).stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
