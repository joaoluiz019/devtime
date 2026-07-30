package com.devtime.client;

import com.devtime.client.domain.Contact;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RN-406: no máximo um contato principal por cliente (spec 003 §22.3).
 *
 * <p>Marcar um novo principal <b>desmarca o anterior automaticamente</b>, na mesma transação
 * (CX-08). Rejeitar a marcação obrigaria o usuário a desmarcar antes, um passo sem valor: a
 * intenção de "este é o principal" é inequívoca.
 *
 * <p>Excluir o contato principal deixa o cliente sem principal, sem promoção automática (CX-09) —
 * escolher o substituto é decisão do usuário, não do sistema (PR-03).
 */
@Component
@RequiredArgsConstructor
public class PrimaryContactPolicy {

    private final ContactRepository contactRepository;

    /**
     * Garante a unicidade antes de persistir o novo principal.
     *
     * @param clientId cliente dono do contato
     * @param newPrimaryId contato que passará a ser principal; nulo na criação
     */
    public void demoteCurrentPrimary(UUID clientId, UUID newPrimaryId) {
        contactRepository
                .findPrimaryByClientId(clientId)
                .filter(current -> !current.getId().equals(newPrimaryId))
                .ifPresent(current -> current.setPrimary(false));
        // O flush ocorre antes do INSERT do novo principal por ordenação do contexto de
        // persistência;
        // o índice único parcial uq_contacts_primary é a segunda barreira contra corrida.
        contactRepository.flush();
    }

    /**
     * CX-07: mais de um principal na mesma requisição é rejeitado antes de qualquer persistência.
     */
    public void assertSinglePrimary(java.util.List<Contact> contacts) {
        long primaries = contacts.stream().filter(Contact::isPrimary).count();
        if (primaries > 1) {
            throw com.devtime.client.domain.ClientExceptions.multiplePrimaryContacts(); // RN-406
        }
    }
}
