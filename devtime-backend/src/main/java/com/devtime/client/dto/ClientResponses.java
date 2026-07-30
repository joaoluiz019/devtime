package com.devtime.client.dto;

import com.devtime.client.domain.ClientStatus;
import com.devtime.client.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de saída da feature 003 (clients.md §5, §7 e §9.2). */
public final class ClientResponses {

    private ClientResponses() {}

    @Schema(name = "AddressResponse")
    public record AddressResponse(
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state,
            String postalCode,
            String country) {}

    @Schema(name = "ContactResponse")
    public record ContactResponse(
            UUID id,
            String name,
            String email,
            String phone,
            String role,
            boolean isPrimary,
            boolean receivesReports,
            long version) {}

    /**
     * Detalhe do cliente (clients.md §7).
     *
     * <p>Os blocos {@code contracts} e {@code stats} de §7 não são emitidos aqui: {@code contracts}
     * é servido por {@code GET /clients/{id}/contracts} (permissão {@code CONTRACT_VIEW}, §4) e
     * {@code stats} depende de tickets e work logs, introduzidos por {@code 007} e {@code 008}.
     *
     * @param availableActions ME-06: reflete o estado atual <b>e</b> as permissões do requisitante
     */
    @Schema(name = "ClientResponse")
    public record ClientResponse(
            UUID id,
            String name,
            String legalName,
            DocumentType documentType,
            String documentNumber,
            String email,
            String phone,
            String website,
            AddressResponse address,
            String notes,
            String color,
            ClientStatus status,
            int activeContractsCount,
            List<ContactResponse> contacts,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<String> availableActions) {}

    /** Item da listagem (clients.md §5) — projeção, nunca a entidade completa (BR-107). */
    @Schema(name = "ClientListItemResponse")
    public record ClientListItemResponse(
            UUID id,
            String name,
            String legalName,
            DocumentType documentType,
            String documentNumber,
            String email,
            String phone,
            String color,
            ClientStatus status,
            int activeContractsCount,
            Instant createdAt) {}

    /** clients.md §9.2. */
    @Schema(name = "ClientDeactivationResponse")
    public record ClientDeactivationResponse(ClientStatus status, DeactivationImpact impact) {}

    /**
     * @param activeContractsUnaffected contratos que continuam operando (RN-407)
     */
    @Schema(name = "DeactivationImpact")
    public record DeactivationImpact(int activeContractsUnaffected, String message) {}
}
