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

    /**
     * Identificação enxuta do cliente, embutida em recursos de outras features.
     *
     * <p>Existe porque um ticket exibe a que cliente pertence (tickets.md §7) e {@code MEMBER}
     * enxerga <b>todos</b> os tickets do tenant (§9 de permissions.md, OB-04 de specs/007). Sem
     * esta referência, o escopo de dados da nota ² — que restringe a <b>carteira</b> de clientes —
     * também esconderia o nome do cliente dentro de um ticket que o membro tem direito de ver,
     * tornando a tela inútil.
     *
     * <p>A consequência aceita e documentada em OB-04 é que {@code MEMBER} enxerga o nome dos
     * clientes através dos tickets. O que a nota ² protege continua protegido: listar e detalhar
     * clientes segue restrito ao vínculo.
     */
    @Schema(name = "ClientRef")
    public record ClientRef(UUID id, String name, String color) {}

    /**
     * Cliente como o cabeçalho de um relatório precisa dele (RN-703, {@code reports.md} §6).
     *
     * <p>Distinta de {@link ClientRef} porque um documento enviado ao cliente final o identifica
     * pela razão social e pelo documento fiscal, não pelo nome curto e pela cor da etiqueta.
     * Distinta de {@link ClientResponse} porque aquela carrega contatos, contadores e ações
     * disponíveis — dados de tela que não pertencem a um cabeçalho congelado em snapshot.
     *
     * <p>É esta a forma que {@code 011} grava no payload do snapshot no fechamento (ADR-036 RP-01):
     * o valor, nunca o ponteiro. Guardar apenas {@code clientId} faria o nome vir da tabela e,
     * portanto, mudar quando o cadastro fosse corrigido — exatamente o que RN-701 impede.
     */
    @Schema(name = "ClientReportParty")
    public record ClientReportParty(
            UUID id,
            String name,
            String legalName,
            String documentType,
            String documentNumber,
            String email,
            String phone,
            AddressResponse address) {}

    /** clients.md §9.2. */
    @Schema(name = "ClientDeactivationResponse")
    public record ClientDeactivationResponse(ClientStatus status, DeactivationImpact impact) {}

    /**
     * @param activeContractsUnaffected contratos que continuam operando (RN-407)
     */
    @Schema(name = "DeactivationImpact")
    public record DeactivationImpact(int activeContractsUnaffected, String message) {}
}
