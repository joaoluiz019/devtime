package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.client.domain.ClientStatus;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros da listagem de clientes (clients.md §5).
 *
 * <p>BR-169: consulta dinâmica por {@code Specification}, com parâmetros vinculados — nunca
 * concatenação de SQL (BR-168).
 */
public final class ClientSpecifications {

    private ClientSpecifications() {}

    /**
     * Busca em nome, razão social e documento (clients.md §5).
     *
     * <p>A comparação remove acentos dos <b>dois</b> lados: o termo digitado é normalizado aqui, e
     * o lado da coluna usa {@code lower(unaccent-like)} obtido por {@code translate}, disponível
     * sem extensão adicional. Isso atende CA-04 ("a busca ignora acentos e diferenças de caixa")
     * sem exigir a extensão {@code unaccent}, que não consta de V001.
     */
    public static Specification<Client> matching(
            String search, ClientStatus status, Boolean hasActiveContracts, String documentNumber) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String term = "%" + unaccented(search) + "%";
                predicates.add(
                        builder.or(
                                builder.like(unaccentedPath(builder, root.get("name")), term),
                                builder.like(unaccentedPath(builder, root.get("legalName")), term),
                                builder.like(builder.lower(root.get("documentNumber")), term)));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (hasActiveContracts != null) {
                predicates.add(
                        hasActiveContracts
                                ? builder.greaterThan(root.get("activeContractsCount"), 0)
                                : builder.equal(root.get("activeContractsCount"), 0));
            }
            if (documentNumber != null && !documentNumber.isBlank()) {
                // Busca exata (clients.md §5), sobre o documento já normalizado.
                predicates.add(builder.equal(root.get("documentNumber"), documentNumber));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Caracteres acentuados usados em português, mapeados para suas formas simples. */
    private static final String ACCENTED = "áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ";

    private static final String UNACCENTED = "aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC";

    private static jakarta.persistence.criteria.Expression<String> unaccentedPath(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> path) {
        return builder.lower(
                builder.function(
                        "translate",
                        String.class,
                        path,
                        builder.literal(ACCENTED),
                        builder.literal(UNACCENTED)));
    }

    static String unaccented(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
