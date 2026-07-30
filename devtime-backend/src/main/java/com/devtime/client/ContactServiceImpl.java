package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.client.domain.ClientExceptions;
import com.devtime.client.domain.Contact;
import com.devtime.client.dto.ClientRequests.ContactRequest;
import com.devtime.client.dto.ClientResponses.ContactResponse;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de contato (RN-406, clients.md §10). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactServiceImpl implements ContactService {

    private static final int MAX_CONTACTS = 20;

    private final ContactRepository contactRepository;
    private final ClientRepository clientRepository;
    private final ClientMapper mapper;
    private final PrimaryContactPolicy primaryContactPolicy;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'CLIENT_VIEW')")
    public List<ContactResponse> listByClient(UUID clientId) {
        requireClient(clientId);
        return mapper.toContactResponses(contactRepository.findByClientId(clientId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_UPDATE')")
    public ContactResponse create(UUID clientId, ContactRequest request) {
        requireClient(clientId);
        if (contactRepository.countByClientId(clientId) >= MAX_CONTACTS) {
            throw ClientExceptions.contactLimitReached(MAX_CONTACTS); // clients.md §10.1
        }

        Contact contact = new Contact();
        contact.setClientId(clientId);
        applyRequest(contact, request);

        if (contact.isPrimary()) {
            primaryContactPolicy.demoteCurrentPrimary(clientId, null); // RN-406
        }
        return mapper.toContactResponse(contactRepository.save(contact));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_UPDATE')")
    public ContactResponse update(UUID clientId, UUID contactId, ContactRequest request) {
        Contact contact = requireContact(clientId, contactId);
        boolean becomingPrimary = Boolean.TRUE.equals(request.isPrimary()) && !contact.isPrimary();

        applyRequest(contact, request);
        if (becomingPrimary) {
            primaryContactPolicy.demoteCurrentPrimary(clientId, contactId); // RN-406, CX-08
        }
        return mapper.toContactResponse(contact);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_UPDATE')")
    public void delete(UUID clientId, UUID contactId) {
        Contact contact = requireContact(clientId, contactId);
        // CX-09: sem promoção automática de substituto — escolher é decisão do usuário (PR-03).
        contactRepository.softDelete(
                contact.getId(), clock.now(), tenantContext.currentUserId().orElse(null));
    }

    private void applyRequest(Contact contact, ContactRequest request) {
        contact.setName(request.name().trim());
        contact.setEmail(
                request.email() == null ? null : request.email().trim().toLowerCase(Locale.ROOT));
        contact.setPhone(request.phone());
        contact.setRole(request.role());
        contact.setPrimary(Boolean.TRUE.equals(request.isPrimary()));
        contact.setReceivesReports(Boolean.TRUE.equals(request.receivesReports()));
    }

    private Client requireClient(UUID clientId) {
        return clientRepository
                .findById(clientId)
                .orElseThrow(() -> EntityNotFoundException.of(Client.class, clientId));
    }

    /**
     * Contato do cliente informado.
     *
     * <p>A verificação de pertencimento evita que um {@code contactId} válido de outro cliente do
     * mesmo tenant seja editado por uma rota aninhada — o filtro de tenant não cobre esse caso.
     */
    private Contact requireContact(UUID clientId, UUID contactId) {
        requireClient(clientId);
        Contact contact =
                contactRepository
                        .findById(contactId)
                        .orElseThrow(() -> EntityNotFoundException.of(Contact.class, contactId));
        if (!contact.getClientId().equals(clientId)) {
            throw EntityNotFoundException.of(Contact.class, contactId);
        }
        return contact;
    }
}
