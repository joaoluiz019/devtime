package com.devtime.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.client.dto.ClientRequests.ContactRequest;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.support.FeatureTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Regras de contato (RN-406, clients.md §10). */
class ContactServiceIntegrationTest extends FeatureTestSupport {

    @Autowired private ClientService clientService;
    @Autowired private ContactService contactService;

    @Test
    @DisplayName("clients.md §10.1: o limite de 20 contatos por cliente é aplicado — DEVTIME-2408")
    void shouldEnforceContactLimit() {
        UUID clientId = client();
        for (int index = 0; index < 20; index++) {
            int position = index;
            asOwnerOfA(
                    () -> contactService.create(clientId, contact("Contato " + position, false)));
        }

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                contactService.create(
                                                        clientId, contact("Excedente", false))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2408");
    }

    @Test
    @DisplayName("clients.md §10: a atualização altera os dados e normaliza o e-mail")
    void shouldUpdateContact() {
        UUID clientId = client();
        UUID contactId =
                asOwnerOfA(() -> contactService.create(clientId, contact("Antigo", false))).id();

        var updated =
                asOwnerOfA(
                        () ->
                                contactService.update(
                                        clientId,
                                        contactId,
                                        new ContactRequest(
                                                "Novo Nome",
                                                "CONTATO@ACME.com.BR",
                                                "+5511988887777",
                                                "Financeiro",
                                                true,
                                                true)));

        assertThat(updated.name()).isEqualTo("Novo Nome");
        assertThat(updated.email()).isEqualTo("contato@acme.com.br");
        assertThat(updated.isPrimary()).isTrue();
        assertThat(updated.receivesReports()).isTrue();
    }

    @Test
    @DisplayName("RN-002: contato de outro cliente não é acessível pela rota aninhada")
    void shouldRejectContactFromAnotherClient() {
        UUID firstClient = client();
        UUID secondClient = client();
        UUID contactId =
                asOwnerOfA(() -> contactService.create(firstClient, contact("Alheio", false))).id();

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                contactService.update(
                                                        secondClient,
                                                        contactId,
                                                        contact("Invasor", false))))
                .as(
                        "um contactId válido de outro cliente do mesmo tenant não pode ser editado aqui")
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("RN-002: cliente inexistente na rota aninhada resulta em 404")
    void shouldRejectContactForUnknownClient() {
        assertThatThrownBy(() -> asOwnerOfA(() -> contactService.listByClient(UUID.randomUUID())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("entities.md §8: a exclusão do cliente aplica cascata lógica aos contatos")
    void deletingClientShouldCascadeToContacts() {
        UUID clientId = client();
        asOwnerOfA(() -> contactService.create(clientId, contact("Some Junto", false)));

        asOwnerOfA(
                () -> {
                    clientService.delete(clientId);
                    return null;
                });

        assertThatThrownBy(() -> asOwnerOfA(() -> contactService.listByClient(clientId)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private UUID client() {
        return asOwnerOfA(
                        () ->
                                clientService.create(
                                        new ClientCreateRequest(
                                                "Cliente " + UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                List.of())))
                .id();
    }

    private ContactRequest contact(String name, boolean primary) {
        return new ContactRequest(name, null, null, null, primary, false);
    }
}
