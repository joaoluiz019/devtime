package com.devtime.contract;

import com.devtime.contract.domain.ContractStatus;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.contract.dto.ContractRequests.ContractDuplicateRequest;
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

    /**
     * contracts.md §4.1: cria um novo contrato em {@code DRAFT} copiando a configuração deste.
     *
     * <p>Copia <b>configuração</b>, nunca <b>histórico</b>: períodos, saldos, ajustes, snapshots e
     * registros de horas pertencem ao contrato de origem e não têm sentido no novo. A cópia nasce
     * em {@code DRAFT} pelo mesmo motivo de qualquer criação — quem ativa é uma decisão explícita
     * (RN-209), e uma cópia ativada por engano geraria período e passaria a aceitar horas.
     */
    ContractResponse duplicate(UUID id, ContractDuplicateRequest request);

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

    /**
     * RN-602: limiares de consumo configurados no contrato.
     *
     * <p>Interface pública para {@code 013-notifications}. Devolve os valores do contrato, nunca
     * 50/80/100 fixos (CP-05): um contrato configurado com {@code [70, 90]} precisa alertar em 70 e
     * 90, e usar valores fixos faria a notificação divergir do painel do mesmo contrato.
     */
    java.util.List<Integer> notificationThresholdsOf(UUID contractId);

    /**
     * Contrato na forma que {@code 012-reports} consome (AR-02).
     *
     * <p>Interface pública para {@code 012}. Distinta de {@link #getById(UUID)} porque aquela expõe
     * {@code ContractType} e {@code ContractStatus}, enums do domínio desta feature (ART-065). As
     * taxas continuam mascaradas sem {@code CONTRACT_VIEW_FINANCIAL}, aqui e não no consumidor
     * (SG-03): o relatório recebe nulo e omite as colunas monetárias sem precisar saber por quê.
     */
    com.devtime.contract.dto.ContractResponses.ContractReportRef getReportRef(UUID contractId);

    /**
     * Cartões de contrato do painel (specs/010 §6.1 CP-02, §16).
     *
     * <p>Interface pública para {@code 010-dashboard}. Devolve os contratos {@code ACTIVE} e {@code
     * SUSPENDED} com o período <b>corrente</b> — o que contém a data de hoje no fuso do tenant — já
     * resolvido, e os limiares de notificação do contrato.
     *
     * <p>A ordenação final por criticidade (CP-02) é do painel, não daqui: ela depende da
     * severidade, que depende do saldo, que pertence a {@code 011}. Esta consulta devolve por
     * código, ordem estável e barata.
     *
     * @param restrictToLinked quando verdadeiro, restringe aos contratos vinculados ao usuário
     *     autenticado (permissions.md §9, nota ²); é o escopo {@code USER} de CP-01
     */
    java.util.List<com.devtime.contract.dto.ContractResponses.ContractDashboardCard>
            findActiveForDashboard(boolean restrictToLinked);

    /**
     * RN-606: contratos cujo {@code endDate} é exatamente a data informada, em <b>todos</b> os
     * tenants.
     *
     * <p>Interface pública para o job de lembrete de {@code 013}. Percorre tenants porque o job é
     * de plataforma; o contexto é definido pelo chamador a cada iteração (BR-049).
     */
    java.util.List<com.devtime.contract.dto.ContractResponses.ContractReminderView> findEndingOn(
            java.time.LocalDate endDate);
}
