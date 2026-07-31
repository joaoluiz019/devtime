package com.devtime.client;

import com.devtime.client.domain.ClientStatus;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.client.dto.ClientRequests.ClientUpdateRequest;
import com.devtime.client.dto.ClientRequests.DeactivateClientRequest;
import com.devtime.client.dto.ClientResponses.ClientDeactivationResponse;
import com.devtime.client.dto.ClientResponses.ClientListItemResponse;
import com.devtime.client.dto.ClientResponses.ClientResponse;
import com.devtime.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Interface pública da feature 003 (spec §22.2).
 *
 * <p>{@link #getActiveForContract(UUID)} é o contrato consumido por {@code 004-contracts} para
 * aplicar RN-201 e RN-405. Nenhuma feature acessa {@link ClientRepository} diretamente (AR-02).
 */
public interface ClientService {

    PageResponse<ClientListItemResponse> search(
            String search,
            ClientStatus status,
            Boolean hasActiveContracts,
            String documentNumber,
            Pageable pageable);

    ClientResponse getById(UUID id);

    ClientResponse create(ClientCreateRequest request);

    ClientResponse update(UUID id, ClientUpdateRequest request);

    /** state-machines.md §4.4: {@code INACTIVE → ACTIVE}. */
    ClientResponse activate(UUID id);

    /** RN-405/RN-407: bloqueia novos contratos; os existentes seguem operando. */
    ClientDeactivationResponse deactivate(UUID id, DeactivateClientRequest request);

    /** RN-401: exclusão lógica, restrita por contratos ativos. */
    void delete(UUID id);

    /**
     * Cliente {@code ACTIVE} do tenant, para vínculo de contrato.
     *
     * <p>Consumido por {@code 004-contracts} (RN-201, RN-405). Retorna DTO e não a entidade: AR-02
     * proíbe que outra feature dependa de {@code client.domain}.
     *
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} se inexistente
     *     ou de outro tenant
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2405} se inativo
     */
    ClientResponse getActiveForContract(UUID clientId);

    /**
     * Identificação do cliente para embutir em recursos de outras features.
     *
     * <p>Interface pública para {@code 004-contracts} e, através dela, para {@code 007-tickets}.
     * <b>Não</b> aplica o escopo de dados de {@code MEMBER} da nota ² — ver a justificativa em
     * {@link com.devtime.client.dto.ClientResponses.ClientRef}. O filtro de tenant continua valendo
     * (ART-022): um cliente de outro tenant resulta em {@code 404}.
     */
    com.devtime.client.dto.ClientResponses.ClientRef getRefById(UUID clientId);

    /**
     * Ajusta {@code activeContractsCount} após uma transição de contrato (entities.md §9).
     *
     * <p>Existe para que a atualização do campo desnormalizado permaneça dentro da feature dona do
     * dado: {@code 004} informa o efeito, {@code 003} decide como registrá-lo.
     *
     * @param delta {@code +1} na ativação, {@code -1} no encerramento ou cancelamento
     */
    void adjustActiveContractsCount(UUID clientId, int delta);
}
