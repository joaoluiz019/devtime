package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
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
 * <p>A definição operacional de "cliente vinculado" (§9) é derivada de work logs e tickets:
 *
 * <pre>
 * contrato C é visível para o membro M se existe work_log W com W.contract_id = C.id e
 * W.user_id = M.id, OU existe ticket T com T.contract_id = C.id e (T.assignee_id = M.id OU
 * T.reporter_id = M.id)
 * </pre>
 *
 * <p>Nem {@code work_logs} nem {@code tickets} existem nesta sprint — são introduzidas por {@code
 * 008} e {@code 007}. O conjunto de clientes vinculados é, portanto, <b>provadamente vazio</b>, e é
 * isso que esta especificação expressa. A alternativa — conceder visão total a {@code MEMBER} até
 * que as tabelas existam — abriria a carteira de clientes ao papel que a nota ² restringe, e
 * ART-085 exige negar por padrão. As subconsultas {@code EXISTS} entram junto com as tabelas.
 */
@Component
@RequiredArgsConstructor
public class MemberClientScopeSpecification {

    private final TenantContext tenantContext;

    /**
     * @return especificação sempre verdadeira para papéis de visão total; restrição de escopo para
     *     {@code MEMBER}
     */
    public Specification<Client> forCurrentRole() {
        boolean restricted = tenantContext.currentRole().filter(Role.MEMBER::equals).isPresent();
        if (!restricted) {
            return (root, query, builder) -> builder.conjunction();
        }
        return (root, query, builder) -> builder.disjunction();
    }

    /** CE-P-05: acesso direto por id fora do escopo resulta em {@code 404}, nunca {@code 403}. */
    public boolean isWithinScope() {
        return tenantContext.currentRole().filter(Role.MEMBER::equals).isEmpty();
    }
}
