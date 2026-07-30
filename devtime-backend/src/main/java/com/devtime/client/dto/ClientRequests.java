package com.devtime.client.dto;

import com.devtime.client.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTOs de entrada da feature 003 (clients.md §6 e §10, spec §23).
 *
 * <p>{@code status} está ausente de todos: ME-05 exige que transições ocorram por endpoint de ação
 * dedicado ({@code /activate}, {@code /deactivate}), nunca por {@code PATCH} no campo.
 */
public final class ClientRequests {

    /** E.164 (clients.md §6). */
    public static final String PHONE_PATTERN = "^\\+?[0-9]{8,20}$";

    public static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";

    private ClientRequests() {}

    /** Value Object {@code Address} no contrato da API (entities.md §7.1). */
    @Schema(name = "AddressRequest")
    public record AddressRequest(
            @Size(max = 200) String street,
            @Size(max = 20) String number,
            @Size(max = 100) String complement,
            @Size(max = 100) String district,
            @Size(max = 100) String city,
            @Size(max = 50) String state,
            @Size(max = 20) String postalCode,
            @Size(min = 2, max = 2) String country) {}

    /** clients.md §10.1. */
    @Schema(name = "ContactRequest")
    public record ContactRequest(
            @NotBlank @Size(min = 2, max = 150) String name,
            @Email @Size(max = 255) String email,
            @Pattern(regexp = PHONE_PATTERN) @Size(max = 20) String phone,
            @Size(max = 80) String role,
            Boolean isPrimary,
            Boolean receivesReports) {}

    /** clients.md §6. */
    @Schema(name = "ClientCreateRequest")
    public record ClientCreateRequest(
            @NotBlank @Size(min = 2, max = 150) String name,
            @Size(max = 200) String legalName,
            DocumentType documentType,
            @Size(max = 20) String documentNumber,
            @Email @Size(max = 255) String email,
            @Pattern(regexp = PHONE_PATTERN) @Size(max = 20) String phone,
            @Pattern(regexp = "^https?://.+") @Size(max = 255) String website,
            @Valid AddressRequest address,
            @Size(max = 4000) String notes,
            @Pattern(regexp = HEX_COLOR) String color,
            @Valid List<ContactRequest> contacts) {

        /**
         * BR-103: validação cruzada no próprio record.
         *
         * <p>CX-07: dois contatos principais na mesma requisição são rejeitados antes de qualquer
         * persistência — corrigir depois exigiria desfazer escritas já feitas.
         */
        @AssertTrue(message = "Apenas um contato pode ser principal")
        public boolean isSinglePrimaryContact() {
            if (contacts == null) {
                return true;
            }
            return contacts.stream()
                            .filter(contact -> Boolean.TRUE.equals(contact.isPrimary()))
                            .count()
                    <= 1;
        }

        /** Documento informado sem tipo não pode ser validado por RN-402. */
        @AssertTrue(message = "documentType é obrigatório quando documentNumber é informado")
        public boolean isDocumentTypeConsistent() {
            return documentNumber == null || documentNumber.isBlank() || documentType != null;
        }
    }

    /**
     * clients.md §9.1.
     *
     * <p>{@code version} é obrigatório: RN-004 exige que toda alteração declare a versão que
     * pretende substituir.
     */
    @Schema(name = "ClientUpdateRequest")
    public record ClientUpdateRequest(
            @NotBlank @Size(min = 2, max = 150) String name,
            @Size(max = 200) String legalName,
            DocumentType documentType,
            @Size(max = 20) String documentNumber,
            @Email @Size(max = 255) String email,
            @Pattern(regexp = PHONE_PATTERN) @Size(max = 20) String phone,
            @Pattern(regexp = "^https?://.+") @Size(max = 255) String website,
            @Valid AddressRequest address,
            @Size(max = 4000) String notes,
            @Pattern(regexp = HEX_COLOR) String color,
            @NotNull Long version) {

        @AssertTrue(message = "documentType é obrigatório quando documentNumber é informado")
        public boolean isDocumentTypeConsistent() {
            return documentNumber == null || documentNumber.isBlank() || documentType != null;
        }
    }

    /** clients.md §9.2: {@code confirmed} é obrigatório quando há contratos ativos (RN-407). */
    @Schema(name = "DeactivateClientRequest")
    public record DeactivateClientRequest(
            Boolean confirmActiveContracts, @Size(max = 500) String reason) {}
}
