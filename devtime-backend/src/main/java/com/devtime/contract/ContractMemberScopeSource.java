package com.devtime.contract;

import com.devtime.client.MemberScopeSource;
import com.devtime.contract.domain.Contract;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribuição de {@code 004-contracts} ao escopo de clientes de {@code MEMBER} (permissions.md
 * §9).
 *
 * <p>Fecha a segunda metade da definição operacional da nota ²: "clientes dos meus contratos". Os
 * contratos vinculados vêm das fontes de {@link MemberContractLinkSource} — hoje os tickets em que
 * o membro é relator ou responsável; em {@code 008}, também os work logs que ele registrou.
 *
 * <p>Implementar {@link MemberScopeSource} não cria aresta nova no grafo de features: {@code
 * contract} já depende de {@code client}.
 */
@Component
@RequiredArgsConstructor
public class ContractMemberScopeSource implements MemberScopeSource {

    private final ContractRepository repository;

    /** Vazia enquanto nenhuma feature de vínculo estiver presente; o escopo fica fechado. */
    private final List<MemberContractLinkSource> linkSources;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> linkedClientIdsOf(UUID userId) {
        Set<UUID> contractIds =
                linkSources.stream()
                        .flatMap(source -> source.contractIdsLinkedTo(userId).stream())
                        .collect(Collectors.toSet());
        if (contractIds.isEmpty()) {
            return Set.of();
        }
        // O filtro de tenant restringe a consulta (ART-022): um contrato de outro tenant não
        // chegaria aqui, mas mesmo que chegasse não produziria vínculo.
        return repository.findAllById(contractIds).stream()
                .map(Contract::getClientId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
