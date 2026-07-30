package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.client.domain.ClientExceptions;
import com.devtime.client.domain.ClientStatus;
import com.devtime.client.domain.Contact;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.client.dto.ClientRequests.ClientUpdateRequest;
import com.devtime.client.dto.ClientRequests.ContactRequest;
import com.devtime.client.dto.ClientRequests.DeactivateClientRequest;
import com.devtime.client.dto.ClientResponses.ClientDeactivationResponse;
import com.devtime.client.dto.ClientResponses.ClientListItemResponse;
import com.devtime.client.dto.ClientResponses.ClientResponse;
import com.devtime.client.dto.ClientResponses.DeactivationImpact;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.pagination.PageRequestFactory;
import com.devtime.shared.pagination.PageResponse;
import com.devtime.shared.security.Permission;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de cadastro de clientes (spec 003 §6).
 *
 * <p>A ordem das validações de {@link #create} segue exatamente a §6.1 da spec e é normativa
 * (BR-062): o formato do documento é verificado <b>antes</b> da unicidade porque um documento
 * inválido não deve consumir consulta ao banco, e porque "CPF inválido" é mensagem mais útil que
 * "CPF já cadastrado" quando ambas se aplicam.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ClientServiceImpl implements ClientService {

    private static final int MAX_CONTACTS = 20;

    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;
    private final ClientMapper mapper;
    private final DocumentNormalizer documentNormalizer;
    private final DocumentValidator documentValidator;
    private final ClientColorGenerator colorGenerator;
    private final ClientDeletionGuard deletionGuard;
    private final PrimaryContactPolicy primaryContactPolicy;
    private final MemberClientScopeSpecification memberScope;
    private final PageRequestFactory pageRequestFactory;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'CLIENT_VIEW')")
    public PageResponse<ClientListItemResponse> search(
            String search,
            ClientStatus status,
            Boolean hasActiveContracts,
            String documentNumber,
            Pageable pageable) {
        Pageable validated = pageRequestFactory.validate(pageable); // RN-012
        Specification<Client> specification =
                ClientSpecifications.matching(
                                search,
                                status,
                                hasActiveContracts,
                                documentNormalizer.normalize(documentNumber))
                        // IMP-02: o escopo de MEMBER entra na consulta, não no resultado.
                        .and(memberScope.forCurrentRole());
        Page<Client> page = clientRepository.findAll(specification, validated);
        return PageResponse.of(page, mapper::toListItem);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CLIENT_VIEW')")
    public ClientResponse getById(UUID id) {
        return toDetail(requireVisible(id));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_CREATE')")
    public ClientResponse create(ClientCreateRequest request) {
        String name = request.name().trim();
        String document = documentNormalizer.normalize(request.documentNumber()); // CX-03

        documentValidator.assertValid(request.documentType(), document); // passo 3 — RN-402
        assertNameIsUnique(name, null); // passo 4 — RN-404
        assertDocumentIsUnique(document, null); // passo 5 — RN-403

        Client client = new Client();
        client.setName(name);
        client.setLegalName(request.legalName());
        client.setDocumentType(request.documentType());
        client.setDocumentNumber(document);
        client.setEmail(normalizeEmail(request.email()));
        client.setPhone(request.phone());
        client.setWebsite(request.website());
        client.setAddress(mapper.toAddress(request.address()));
        client.setNotes(request.notes());
        client.setColor(request.color() == null ? colorGenerator.generate(name) : request.color());
        client.setStatus(ClientStatus.ACTIVE);
        client.setActiveContractsCount(0);

        Client saved = clientRepository.save(client);
        createContacts(saved.getId(), request.contacts());

        log.info("cliente criado clientId={} status={}", saved.getId(), saved.getStatus());
        return toDetail(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_UPDATE')")
    public ClientResponse update(UUID id, ClientUpdateRequest request) {
        Client client = requireVisible(id);
        assertVersion(client, request.version()); // RN-004

        String name = request.name().trim();
        String document = documentNormalizer.normalize(request.documentNumber());

        documentValidator.assertValid(request.documentType(), document); // RN-402
        assertNameIsUnique(name, id); // RN-404
        assertDocumentIsUnique(document, id); // RN-403

        client.setName(name);
        client.setLegalName(request.legalName());
        client.setDocumentType(request.documentType());
        client.setDocumentNumber(document);
        client.setEmail(normalizeEmail(request.email()));
        client.setPhone(request.phone());
        client.setWebsite(request.website());
        client.setAddress(mapper.toAddress(request.address()));
        client.setNotes(request.notes());
        if (request.color() != null) {
            client.setColor(request.color());
        }
        return toDetail(client);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_UPDATE')")
    public ClientResponse activate(UUID id) {
        Client client = requireVisible(id);
        client.setStatus(ClientStatus.ACTIVE); // state-machines.md §4.4
        log.info("cliente reativado clientId={}", id);
        return toDetail(client);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_UPDATE')")
    public ClientDeactivationResponse deactivate(UUID id, DeactivateClientRequest request) {
        Client client = requireVisible(id);
        int activeContracts = client.getActiveContractsCount();

        // RN-407: a confirmação é exigida apenas quando há impacto a comunicar.
        boolean confirmed =
                request != null && Boolean.TRUE.equals(request.confirmActiveContracts());
        if (activeContracts > 0 && !confirmed) {
            throw ClientExceptions.deactivationConfirmationRequired(activeContracts);
        }

        client.setStatus(ClientStatus.INACTIVE); // RN-405
        log.info("cliente inativado clientId={} contratosAtivos={}", id, activeContracts);
        return new ClientDeactivationResponse(
                client.getStatus(),
                new DeactivationImpact(
                        activeContracts, "Contratos ativos continuam operando normalmente."));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CLIENT_DELETE')")
    public void delete(UUID id) {
        Client client = requireVisible(id);
        deletionGuard.assertDeletable(client); // RN-401

        UUID actor = tenantContext.currentUserId().orElse(null);
        // Cascata lógica (entities.md §8): os contatos acompanham o cliente.
        contactRepository
                .findByClientId(id)
                .forEach(
                        contact ->
                                contactRepository.softDelete(contact.getId(), clock.now(), actor));
        clientRepository.softDelete(id, clock.now(), actor); // RN-003
        log.info("cliente excluído clientId={}", id);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_CREATE')")
    public ClientResponse getActiveForContract(UUID clientId) {
        Client client =
                clientRepository
                        .findById(clientId)
                        .orElseThrow(() -> EntityNotFoundException.of(Client.class, clientId));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw ClientExceptions.inactive(clientId); // RN-201, RN-405
        }
        return toDetail(client);
    }

    @Override
    @Transactional
    public void adjustActiveContractsCount(UUID clientId, int delta) {
        Client client =
                clientRepository
                        .findById(clientId)
                        .orElseThrow(() -> EntityNotFoundException.of(Client.class, clientId));
        // CHECK (>= 0) na coluna: um decremento abaixo de zero indica contador divergente, e
        // rejeitar é preferível a mascarar o desvio até a reconciliação noturna.
        client.setActiveContractsCount(Math.max(0, client.getActiveContractsCount() + delta));
    }

    private void createContacts(UUID clientId, List<ContactRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        if (requests.size() > MAX_CONTACTS) {
            throw ClientExceptions.contactLimitReached(MAX_CONTACTS);
        }
        List<Contact> contacts = new ArrayList<>();
        for (ContactRequest request : requests) {
            Contact contact = new Contact();
            contact.setClientId(clientId);
            contact.setName(request.name().trim());
            contact.setEmail(normalizeEmail(request.email()));
            contact.setPhone(request.phone());
            contact.setRole(request.role());
            contact.setPrimary(Boolean.TRUE.equals(request.isPrimary()));
            contact.setReceivesReports(Boolean.TRUE.equals(request.receivesReports()));
            contacts.add(contact);
        }
        primaryContactPolicy.assertSinglePrimary(contacts); // RN-406, CX-07
        contactRepository.saveAll(contacts);
    }

    private Client requireVisible(UUID id) {
        Client client =
                clientRepository
                        .findById(id)
                        .orElseThrow(() -> EntityNotFoundException.of(Client.class, id));
        if (!memberScope.isWithinScope()) {
            // CE-P-05 / CX-13: fora do escopo do papel é 404, nunca 403 — 403 confirmaria que o
            // cliente existe.
            throw EntityNotFoundException.of(Client.class, id);
        }
        return client;
    }

    private void assertNameIsUnique(String name, UUID excludedId) {
        if (clientRepository.existsByNameIgnoreCase(name, excludedId)) {
            throw ClientExceptions.duplicateName(); // RN-404
        }
    }

    private void assertDocumentIsUnique(String document, UUID excludedId) {
        if (document != null && clientRepository.existsByDocumentNumber(document, excludedId)) {
            throw ClientExceptions.duplicateDocument(); // RN-403
        }
    }

    private void assertVersion(Client client, long expected) {
        if (client.getVersion() != null && client.getVersion() != expected) {
            throw BusinessRuleException.versionConflict("Client", expected); // RN-004
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private ClientResponse toDetail(Client client) {
        ClientResponse base = mapper.toResponse(client);
        return new ClientResponse(
                base.id(),
                base.name(),
                base.legalName(),
                base.documentType(),
                base.documentNumber(),
                base.email(),
                base.phone(),
                base.website(),
                base.address(),
                base.notes(),
                base.color(),
                base.status(),
                base.activeContractsCount(),
                mapper.toContactResponses(contactRepository.findByClientId(client.getId())),
                base.createdAt(),
                base.updatedAt(),
                base.version(),
                availableActions(client));
    }

    /**
     * ME-06: as ações refletem o estado atual <b>e</b> as permissões do requisitante.
     *
     * <p>{@code DELETE} não aparece quando há contratos ativos (RN-401) — a interface não deve
     * oferecer uma ação que o backend rejeitaria.
     */
    private List<String> availableActions(Client client) {
        List<String> actions = new ArrayList<>();
        var permissions = tenantContext.currentPermissions();
        if (permissions.contains(Permission.CLIENT_UPDATE)) {
            actions.add("UPDATE");
            actions.add(client.getStatus() == ClientStatus.ACTIVE ? "DEACTIVATE" : "ACTIVATE");
        }
        if (permissions.contains(Permission.CLIENT_DELETE)
                && client.getActiveContractsCount() == 0) {
            actions.add("DELETE");
        }
        return List.copyOf(actions);
    }
}
