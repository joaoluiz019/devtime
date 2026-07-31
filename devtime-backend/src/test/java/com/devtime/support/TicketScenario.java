package com.devtime.support;

import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientRequests.ClientCreateRequest;
import com.devtime.contract.ContractService;
import com.devtime.contract.domain.ContractType;
import com.devtime.contract.domain.OveragePolicy;
import com.devtime.contract.domain.RolloverPolicy;
import com.devtime.contract.dto.ContractRequests.ContractCreateRequest;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cenário mínimo para exercitar tickets: um cliente ativo e um contrato ativo.
 *
 * <p>BR-207: os dados nascem pelos serviços de produção, nunca por {@code INSERT} de setup — o
 * objeto precisa passar pelo mesmo caminho de persistência, incluindo {@code AuditListener},
 * geração de UUIDv7 e atribuição de tenant.
 *
 * <p>É um bean, e não um utilitário estático, porque depende dos serviços de {@code 003} e {@code
 * 004} — que aplicam suas próprias regras e mantêm o cenário coerente com o domínio.
 */
@Component
@RequiredArgsConstructor
public class TicketScenario {

    /**
     * Coincide com o cenário de {@code ContractServiceIntegrationTest} (§7.2 de business-rules).
     */
    public static final LocalDate CONTRACT_START = LocalDate.of(2026, 1, 10);

    private final ClientService clientService;
    private final ContractService contractService;

    /** Cliente ativo com nome único, para não colidir com RN-404 entre execuções. */
    public UUID activeClient() {
        return clientService
                .create(
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
                                List.of()))
                .id();
    }

    /** Contrato {@code ACTIVE}, apto a receber tickets e registros de horas (RN-306). */
    public ContractResponse activeContract(UUID clientId) {
        return activeContract(clientId, "Sustentação " + UUID.randomUUID());
    }

    public ContractResponse activeContract(UUID clientId, String name) {
        ContractResponse draft = contractService.create(contractRequest(clientId, name));
        contractService.activate(draft.id());
        return contractService.getById(draft.id());
    }

    /** Contrato em {@code DRAFT}: existe no tenant, mas não aceita registros (RN-306). */
    public ContractResponse draftContract(UUID clientId) {
        return contractService.create(contractRequest(clientId, "Rascunho " + UUID.randomUUID()));
    }

    private ContractCreateRequest contractRequest(UUID clientId, String name) {
        return new ContractCreateRequest(
                clientId,
                null,
                name,
                null,
                ContractType.MONTHLY_HOURS,
                2400,
                CONTRACT_START,
                null,
                1,
                RolloverPolicy.NONE,
                null,
                1,
                OveragePolicy.WARN,
                null,
                null,
                "BRL",
                true,
                true,
                null,
                null,
                null);
    }
}
