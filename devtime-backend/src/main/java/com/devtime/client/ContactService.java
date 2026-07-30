package com.devtime.client;

import com.devtime.client.dto.ClientRequests.ContactRequest;
import com.devtime.client.dto.ClientResponses.ContactResponse;
import java.util.List;
import java.util.UUID;

/** Gestão dos contatos de um cliente (clients.md §10). */
public interface ContactService {

    List<ContactResponse> listByClient(UUID clientId);

    ContactResponse create(UUID clientId, ContactRequest request);

    ContactResponse update(UUID clientId, UUID contactId, ContactRequest request);

    /** Exclusão lógica. Remover o principal deixa o cliente sem principal (CX-09). */
    void delete(UUID clientId, UUID contactId);
}
