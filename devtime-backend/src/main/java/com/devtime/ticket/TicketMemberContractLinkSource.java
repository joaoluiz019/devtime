package com.devtime.ticket;

import com.devtime.contract.MemberContractLinkSource;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contribuição de {@code 007-tickets} ao escopo de dados de {@code MEMBER} (permissions.md §9).
 *
 * <p>Metade da definição operacional da nota ²: um contrato é visível ao membro quando existe
 * ticket dele nesse contrato — como relator <b>ou</b> responsável (OWN-04). A outra metade, pelos
 * work logs, chega com {@code 008}.
 *
 * <p>Esta é a pendência que a S3 registrou como "as subconsultas {@code EXISTS} entram com {@code
 * 007}/{@code 008}": até a tabela {@code tickets} existir, o conjunto era provadamente vazio e o
 * escopo permanecia fechado por padrão (ART-085).
 *
 * <p>Implementar {@link MemberContractLinkSource} não cria aresta nova: {@code ticket} já depende
 * de {@code contract}.
 */
@Component
@RequiredArgsConstructor
public class TicketMemberContractLinkSource implements MemberContractLinkSource {

    private final TicketRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> contractIdsLinkedTo(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        return Set.copyOf(repository.findContractIdsByParticipant(userId));
    }
}
