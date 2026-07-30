package com.devtime.client;

import com.devtime.client.dto.ClientRequests.ContactRequest;
import com.devtime.client.dto.ClientResponses.ContactResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de contato do cliente (clients.md §10). */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/contacts")
@RequiredArgsConstructor
@Tag(name = "Contatos", description = "Pessoas de referência dentro do cliente (RN-406)")
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    @Operation(summary = "Lista os contatos do cliente")
    @ApiResponse(responseCode = "200", description = "Contatos do cliente")
    public List<ContactResponse> list(@PathVariable UUID clientId) {
        return contactService.listByClient(clientId);
    }

    @PostMapping
    @Operation(
            summary = "Adiciona um contato",
            description = "RN-406: marcar como principal desmarca o anterior automaticamente.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Contato criado"),
        @ApiResponse(responseCode = "422", description = "DEVTIME-2408 — limite de 20 contatos")
    })
    public ResponseEntity<ContactResponse> create(
            @PathVariable UUID clientId, @Valid @RequestBody ContactRequest request) {
        ContactResponse created = contactService.create(clientId, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/clients/" + clientId + "/contacts/" + created.id()))
                .body(created);
    }

    @PutMapping("/{contactId}")
    @Operation(summary = "Atualiza um contato")
    @ApiResponse(responseCode = "200", description = "Contato atualizado")
    public ContactResponse update(
            @PathVariable UUID clientId,
            @PathVariable UUID contactId,
            @Valid @RequestBody ContactRequest request) {
        return contactService.update(clientId, contactId, request);
    }

    @DeleteMapping("/{contactId}")
    @Operation(
            summary = "Remove um contato (exclusão lógica)",
            description =
                    "Excluir o principal deixa o cliente sem principal, sem promoção automática.")
    @ApiResponse(responseCode = "204", description = "Contato removido")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID contactId) {
        contactService.delete(clientId, contactId);
        return ResponseEntity.noContent().build();
    }
}
