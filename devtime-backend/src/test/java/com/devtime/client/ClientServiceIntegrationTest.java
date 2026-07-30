package com.devtime.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.client.domain.ClientStatus;
import com.devtime.client.domain.DocumentType;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.client.dto.ClientRequests.ClientUpdateRequest;
import com.devtime.client.dto.ClientRequests.ContactRequest;
import com.devtime.client.dto.ClientRequests.DeactivateClientRequest;
import com.devtime.client.dto.ClientResponses.ClientResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** Regras de cadastro de clientes (RN-401 a RN-407, spec 003). */
class ClientServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private ClientService clientService;
    @Autowired private ContactService contactService;

    @Test
    @DisplayName("RN-404/CX-01: nome duplicado sem diferenciar caixa é rejeitado com DEVTIME-2404")
    void shouldRejectDuplicateNameIgnoringCase() {
        asOwnerOfA(() -> clientService.create(request("Acme Corporation", null, null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                clientService.create(
                                                        request("ACME CORPORATION", null, null))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2404");
    }

    @Test
    @DisplayName("CX-12/CE-C-02: nomes que diferem por acento são clientes distintos")
    void shouldAcceptNamesDifferingByAccent() {
        asOwnerOfA(() -> clientService.create(request("Índigo", null, null)));
        asOwnerOfA(() -> clientService.create(request("Indigo", null, null)));

        assertThat(
                        asOwnerOfA(
                                        () ->
                                                clientService.search(
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 20)))
                                .content())
                .hasSize(2);
    }

    @Test
    @DisplayName("RN-403: documento duplicado no tenant é rejeitado com DEVTIME-2403")
    void shouldRejectDuplicateDocument() {
        asOwnerOfA(
                () -> clientService.create(request("Alfa", DocumentType.CNPJ, "11222333000181")));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                clientService.create(
                                                        request(
                                                                "Beta",
                                                                DocumentType.CNPJ,
                                                                "11.222.333/0001-81"))))
                .as("CX-03: a máscara é normalizada antes de comparar")
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2403");
    }

    @Test
    @DisplayName("RN-402: documento inválido é rejeitado antes da verificação de unicidade")
    void shouldValidateDocumentBeforeUniqueness() {
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                clientService.create(
                                                        request(
                                                                "Gama",
                                                                DocumentType.CPF,
                                                                "11111111111"))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2402");
    }

    @Test
    @DisplayName("CX-02: o nome de um cliente excluído pode ser reutilizado (índice parcial)")
    void shouldAllowReusingNameOfDeletedClient() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Efêmero", null, null)).id());
        asOwnerOfA(
                () -> {
                    clientService.delete(id);
                    return null;
                });

        ClientResponse recreated =
                asOwnerOfA(() -> clientService.create(request("Efêmero", null, null)));

        assertThat(recreated.id()).isNotEqualTo(id);
    }

    @Test
    @DisplayName("§6.4: a cor é derivada do nome e é determinística")
    void shouldDeriveColorFromName() {
        ClientResponse first =
                asOwnerOfA(() -> clientService.create(request("Cor Estável", null, null)));
        String expected = new ClientColorGenerator().generate("Cor Estável");

        assertThat(first.color()).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-401: cliente com contrato ativo não pode ser excluído — DEVTIME-2401")
    void shouldRejectDeletionWithActiveContracts() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Com Contrato", null, null)).id());
        asOwnerOfA(
                () -> {
                    clientService.adjustActiveContractsCount(id, +1);
                    return null;
                });

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () -> {
                                            clientService.delete(id);
                                            return null;
                                        }))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2401");
    }

    @Test
    @DisplayName("CE-C-06: cliente sem contratos ativos pode ser excluído")
    void shouldAllowDeletionWithoutActiveContracts() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Sem Contrato", null, null)).id());

        asOwnerOfA(
                () -> {
                    clientService.delete(id);
                    return null;
                });

        assertThatThrownBy(() -> asOwnerOfA(() -> clientService.getById(id)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-407/CX-10: inativar com contratos ativos exige confirmação — DEVTIME-2407")
    void shouldRequireConfirmationToDeactivateWithActiveContracts() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Ativo", null, null)).id());
        asOwnerOfA(
                () -> {
                    clientService.adjustActiveContractsCount(id, +2);
                    return null;
                });

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                clientService.deactivate(
                                                        id,
                                                        new DeactivateClientRequest(false, null))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2407");

        var confirmed =
                asOwnerOfA(
                        () ->
                                clientService.deactivate(
                                        id, new DeactivateClientRequest(true, null)));

        assertThat(confirmed.status()).isEqualTo(ClientStatus.INACTIVE);
        assertThat(confirmed.impact().activeContractsUnaffected())
                .as("RN-407: nenhum contrato é alterado em cascata")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("RN-405: cliente inativo não é aceito para novo contrato — DEVTIME-2405")
    void inactiveClientShouldNotAcceptContracts() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Inativo", null, null)).id());
        asOwnerOfA(() -> clientService.deactivate(id, new DeactivateClientRequest(true, null)));

        assertThatThrownBy(() -> asOwnerOfA(() -> clientService.getActiveForContract(id)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2405");
    }

    @Test
    @DisplayName("CX-11: reativar devolve o cliente a ACTIVE")
    void shouldReactivateClient() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Volta", null, null)).id());
        asOwnerOfA(() -> clientService.deactivate(id, new DeactivateClientRequest(true, null)));

        assertThat(asOwnerOfA(() -> clientService.activate(id)).status())
                .isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    @DisplayName("RN-406/CX-08: marcar um novo contato principal desmarca o anterior")
    void shouldDemotePreviousPrimaryContact() {
        UUID clientId =
                asOwnerOfA(() -> clientService.create(request("Com Contatos", null, null)).id());
        UUID first =
                asOwnerOfA(() -> contactService.create(clientId, contact("Marcelo", true))).id();
        UUID second = asOwnerOfA(() -> contactService.create(clientId, contact("Ana", true))).id();

        var contacts = asOwnerOfA(() -> contactService.listByClient(clientId));

        assertThat(contacts).hasSize(2);
        assertThat(contacts).filteredOn(contact -> contact.isPrimary()).hasSize(1);
        assertThat(contacts)
                .filteredOn(contact -> contact.isPrimary())
                .first()
                .satisfies(contact -> assertThat(contact.id()).isEqualTo(second));
        assertThat(contacts)
                .filteredOn(contact -> contact.id().equals(first))
                .first()
                .satisfies(contact -> assertThat(contact.isPrimary()).isFalse());
    }

    @Test
    @DisplayName("CX-09: excluir o contato principal não promove substituto automaticamente")
    void shouldNotPromoteContactAfterPrimaryDeletion() {
        UUID clientId =
                asOwnerOfA(() -> clientService.create(request("Sem Promoção", null, null)).id());
        UUID primary =
                asOwnerOfA(() -> contactService.create(clientId, contact("Principal", true))).id();
        asOwnerOfA(() -> contactService.create(clientId, contact("Secundário", false)));

        asOwnerOfA(
                () -> {
                    contactService.delete(clientId, primary);
                    return null;
                });

        assertThat(asOwnerOfA(() -> contactService.listByClient(clientId)))
                .filteredOn(contact -> contact.isPrimary())
                .isEmpty();
    }

    @Test
    @DisplayName("CX-07: dois contatos principais na mesma criação são rejeitados — DEVTIME-2406")
    void shouldRejectTwoPrimaryContactsInSameRequest() {
        ClientCreateRequest request =
                new ClientCreateRequest(
                        "Dois Principais",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(contact("A", true), contact("B", true)));

        assertThatThrownBy(() -> asOwnerOfA(() -> clientService.create(request)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2406");
    }

    @Test
    @DisplayName("RN-004: alteração com version divergente retorna DEVTIME-2004")
    void shouldRejectStaleVersion() {
        ClientResponse client =
                asOwnerOfA(() -> clientService.create(request("Concorrente", null, null)));

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                clientService.update(
                                                        client.id(),
                                                        new ClientUpdateRequest(
                                                                "Outro Nome",
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                client.version() + 5))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2004");
    }

    @Test
    @DisplayName("CA-04: a busca ignora acentos e diferenças de caixa")
    void searchShouldIgnoreAccentsAndCase() {
        asOwnerOfA(() -> clientService.create(request("Índigo Soluções", null, null)));

        assertThat(
                        asOwnerOfA(
                                        () ->
                                                clientService.search(
                                                        "indigo",
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 20)))
                                .content())
                .hasSize(1);
    }

    @Test
    @DisplayName("BR-208/RN-002: cliente de outro tenant é invisível e resulta em 404")
    void shouldIsolateClientsBetweenTenants() {
        UUID clientOfA =
                asOwnerOfA(() -> clientService.create(request("Somente do A", null, null)).id());

        assertThat(
                        asOwnerOfB(
                                        () ->
                                                clientService.search(
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 20)))
                                .content())
                .isEmpty();
        assertThatThrownBy(() -> asOwnerOfB(() -> clientService.getById(clientOfA)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("nota ²/CX-13: MEMBER sem vínculo não enxerga clientes e recebe 404 por id")
    void memberScopeShouldHideUnlinkedClients() {
        UUID clientOfA =
                asOwnerOfA(() -> clientService.create(request("Restrito", null, null)).id());

        assertThat(
                        runAs(
                                        tenantAId,
                                        userAId,
                                        Role.MEMBER,
                                        () ->
                                                clientService.search(
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 20)))
                                .content())
                .as("o escopo de MEMBER é aplicado na consulta (IMP-02)")
                .isEmpty();

        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        Role.MEMBER,
                                        () -> clientService.getById(clientOfA)))
                .as("CE-P-05: fora do escopo é 404, nunca 403")
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("permissions.md §7: MEMBER não cria clientes")
    void memberShouldNotCreateClients() {
        assertThatThrownBy(
                        () ->
                                runAs(
                                        tenantAId,
                                        userAId,
                                        Role.MEMBER,
                                        () ->
                                                clientService.create(
                                                        request("Proibido", null, null))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("clients.md §7 / ME-06: availableActions omite DELETE com contratos ativos")
    void availableActionsShouldReflectStateAndPermissions() {
        UUID id = asOwnerOfA(() -> clientService.create(request("Com Ações", null, null)).id());

        assertThat(asOwnerOfA(() -> clientService.getById(id)).availableActions())
                .containsExactlyInAnyOrder("UPDATE", "DEACTIVATE", "DELETE");

        asOwnerOfA(
                () -> {
                    clientService.adjustActiveContractsCount(id, +1);
                    return null;
                });

        assertThat(asOwnerOfA(() -> clientService.getById(id)).availableActions())
                .doesNotContain("DELETE");
    }

    private ClientCreateRequest request(String name, DocumentType type, String document) {
        return new ClientCreateRequest(
                name, null, type, document, null, null, null, null, null, null, null);
    }

    private ContactRequest contact(String name, boolean primary) {
        return new ContactRequest(name, null, null, null, primary, false);
    }
}
