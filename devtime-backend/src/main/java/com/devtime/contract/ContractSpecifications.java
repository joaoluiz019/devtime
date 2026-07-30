package com.devtime.contract;

import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros da listagem de contratos (contracts.md §7).
 *
 * <p>Os filtros derivados de consumo ({@code consumptionRateFrom/To}, {@code hasOverage}) dependem
 * do saldo apurado, que pertence a {@code 011-bank-hours}; não são oferecidos nesta sprint em vez
 * de retornarem resultado calculado sobre zeros, que seria enganoso.
 */
public final class ContractSpecifications {

    private ContractSpecifications() {}

    public static Specification<Contract> matching(
            UUID clientId, ContractStatus status, ContractType type, String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (clientId != null) {
                predicates.add(builder.equal(root.get("clientId"), clientId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.toLowerCase(Locale.ROOT) + "%";
                // A busca por nome do cliente exigiria junção com outra feature (AR-02); o filtro
                // por cliente é feito por clientId, que a interface já possui.
                predicates.add(
                        builder.or(
                                builder.like(builder.lower(root.get("code")), term),
                                builder.like(builder.lower(root.get("name")), term)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
