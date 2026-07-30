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
}
