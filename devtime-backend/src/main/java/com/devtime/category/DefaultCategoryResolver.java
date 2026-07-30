package com.devtime.category;

import com.devtime.category.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cadeia de pré-seleção da categoria (RN-104, spec 005 §6.2).
 *
 * <p>Ordem: ticket → contrato → preferência do usuário → primeira ativa por {@code sortOrder} e
 * depois {@code name}. Vai do mais específico ao mais genérico: o ticket conhece a natureza exata
 * do trabalho, o contrato conhece o tipo de serviço, o usuário conhece seu próprio padrão.
 *
 * <p><b>Origem inativa é pulada, não rejeitada</b> (§6.2): um ticket que aponta para categoria
 * inativada meses atrás não deve impedir o registro de horas — apenas deixa de sugeri-la.
 *
 * <p>Os identificadores das três primeiras origens chegam por parâmetro em vez de serem buscados
 * aqui. Buscar o contrato dentro desta classe criaria a dependência {@code category → contract},
 * enquanto {@code contract → category} já existe por {@code defaultCategoryId} — um ciclo entre
 * features, proibido por AR-09. O chamador ({@code 008-worklogs}) fornece o que já tem em mãos.
 */
@Component
@RequiredArgsConstructor
public class DefaultCategoryResolver {

    private final CategoryRepository repository;

    /**
     * Resolve a categoria padrão para um novo registro de horas.
     *
     * <p>O desempate final por {@code sortOrder} e depois {@code name} torna a resolução
     * determinística: sem ele, dois registros feitos em sequência poderiam receber categorias
     * diferentes sem que nada tivesse mudado.
     *
     * @param ticketCategoryId {@code ticket.defaultCategoryId}, quando houver
     * @param contractCategoryId {@code contract.defaultCategoryId}, quando houver
     * @param userCategoryId {@code user.preferences.defaultCategoryId}, quando houver
     * @return a categoria resolvida; vazio apenas se o tenant não possuir nenhuma categoria ativa
     *     (CX-06 — inativar todas é decisão legítima)
     */
    public Optional<Category> resolveDefault(
            UUID ticketCategoryId, UUID contractCategoryId, UUID userCategoryId) {
        for (UUID candidate : candidates(ticketCategoryId, contractCategoryId, userCategoryId)) {
            Optional<Category> active = activeById(candidate);
            if (active.isPresent()) {
                return active;
            }
        }
        return repository.findActiveOrdered().stream().findFirst();
    }

    private List<UUID> candidates(UUID ticket, UUID contract, UUID user) {
        return java.util.stream.Stream.of(ticket, contract, user)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Optional<Category> activeById(UUID id) {
        return repository.findActiveById(id);
    }
}
