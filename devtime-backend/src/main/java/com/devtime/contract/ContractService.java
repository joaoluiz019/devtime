package com.devtime.contract;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.contract.dto.ContractRequests.ContractTransitionRequest;
import com.devtime.contract.dto.ContractRequests.ContractUpdateRequest;
import com.devtime.contract.dto.ContractResponses.ContractActivationResponse;
import com.devtime.contract.dto.ContractResponses.ContractHistoryResponse;
import com.devtime.contract.dto.ContractResponses.ContractListItemResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.contract.dto.ContractResponses.ContractTransitionResponse;
import com.devtime.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Interface pública da feature 004 (spec §22.2).
 *
 * <p>{@code getActiveForWorkLog} e {@code resolveOpenPeriod} — os contratos consumidos por {@code
 * 007}, {@code 008} e {@code 011} — pertencem a esta interface e a {@link ContractPeriodService}.
 */
public interface ContractService {

    PageResponse<ContractListItemResponse> search(
            UUID clientId,
            ContractStatus status,
            ContractType type,
            String search,
            Pageable pageable);

    ContractResponse getById(UUID id);

    /** Cria em {@code DRAFT}. Nenhum período é gerado antes da ativação (§6.1, passo 9). */
    ContractResponse create(ContractCreateRequest request);

    ContractResponse update(UUID id, ContractUpdateRequest request);

    /** RN-209: gera o primeiro período como {@code OPEN}, na mesma transação (INV-CTR-06). */
    ContractActivationResponse activate(UUID id);

    ContractTransitionResponse suspend(UUID id, ContractTransitionRequest request);

    /** CE-ME-09: gera os períodos faltantes para preservar a contiguidade (INV-PER-03). */
    ContractTransitionResponse resume(UUID id);

    /** RN-214: trunca o período corrente em {@code endDate}. */
    ContractTransitionResponse end(UUID id, ContractTransitionRequest request);

    /** Trunca o período corrente em {@code hoje}. Transição terminal (CE-15). */
    ContractTransitionResponse cancel(UUID id, ContractTransitionRequest request);

    /** RN-205: permitido apenas em {@code DRAFT}. */
    void delete(UUID id);

    /** contracts.md §12.2: série histórica dos períodos do contrato. */
    ContractHistoryResponse history(UUID id, int periods);

    /**
     * Contrato apto a receber registro de horas (RN-306).
     *
     * <p>Interface pública para {@code 007} e {@code 008}. Retorna DTO, não a entidade (AR-02).
     */
    ContractResponse getActiveForWorkLog(UUID contractId);

    /**
     * Identificador do contrato pelo código legível ({@code CT-0001}).
     *
     * <p>Interface pública para {@code 007}: a chave do ticket deriva do código do contrato
     * (RN-302), e a busca por chave (FA-15) precisa percorrer o caminho inverso.
     *
     * @return vazio quando o código não existe no tenant — indistinguível de código de outro
     *     tenant, por ART-024
     */
    java.util.Optional<UUID> findIdByCode(String code);

    /**
     * Contratos de um cliente.
     *
     * <p>Interface pública para {@code 007}: o ticket não desnormaliza o cliente (database.md
     * §7.7), então filtrar tickets por cliente exige resolver os contratos dele primeiro. Devolver
     * a lista permite que o filtro seja aplicado na consulta, nunca em memória (IMP-02).
     */
    java.util.List<UUID> findIdsByClient(UUID clientId);

    /**
     * Referência do contrato para embutir em recursos de outras features.
     *
     * <p>Interface pública para {@code 007-tickets} e {@code 008-worklogs}. Devolve {@code status}
     * como texto e {@code acceptsWorkLogs} já decidido, para que a feature consumidora não precise
     * conhecer {@code ContractStatus} (AR-02) nem reimplementar RN-306.
     *
     * <p>Não aplica o escopo de dados de {@code MEMBER}: o consumidor já verificou a permissão do
     * recurso que a embute, e {@code MEMBER} enxerga todos os tickets do tenant (OB-04 de {@code
     * specs/007}). O filtro de tenant continua valendo.
     */
    com.devtime.contract.dto.ContractResponses.ContractRefResponse getRefById(UUID contractId);

    /**
     * Contrato apto a receber registro de horas, com a vigência e a política de excedente.
     *
     * <p>Interface pública para {@code 008-worklogs} e {@code 009-timer}. Diferente de {@link
     * #getRefById(UUID)}, aplica RN-306 — falha com {@code DEVTIME-2306} quando o contrato está
     * {@code ENDED} ou {@code CANCELLED} — e carrega o que RN-117 e RN-231 exigem.
     *
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} quando
     *     inexistente ou de outro tenant
     */
    com.devtime.contract.dto.ContractResponses.ContractWorkLogRefResponse getWorkLogRef(
            UUID contractId);
}
