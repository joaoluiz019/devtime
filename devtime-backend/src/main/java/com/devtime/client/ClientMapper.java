package com.devtime.client;

import com.devtime.client.domain.Client;
import com.devtime.client.domain.Contact;
import com.devtime.client.dto.ClientRequests.AddressRequest;
import com.devtime.client.dto.ClientResponses.AddressResponse;
import com.devtime.client.dto.ClientResponses.ClientListItemResponse;
import com.devtime.client.dto.ClientResponses.ClientResponse;
import com.devtime.client.dto.ClientResponses.ContactResponse;
import com.devtime.shared.persistence.Address;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão entre {@link Client}/{@link Contact} e DTOs (ADR-014, BR-104).
 *
 * <p>{@code postalCode} no contrato da API corresponde a {@code zipCode} no Value Object
 * compartilhado {@link Address}: o nome de entities.md §7.1 é preservado na API, e o do VO já
 * existente em {@code shared} é preservado no código — mapear é preferível a renomear qualquer um
 * dos dois lados, que afetaria {@code Tenant} ou o contrato publicado.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClientMapper {

    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "availableActions", ignore = true)
    ClientResponse toResponse(Client client);

    ClientListItemResponse toListItem(Client client);

    @Mapping(target = "isPrimary", source = "primary")
    ContactResponse toContactResponse(Contact contact);

    List<ContactResponse> toContactResponses(List<Contact> contacts);

    @Mapping(target = "postalCode", source = "zipCode")
    AddressResponse toAddressResponse(Address address);

    @Mapping(target = "zipCode", source = "postalCode")
    Address toAddress(AddressRequest request);
}
