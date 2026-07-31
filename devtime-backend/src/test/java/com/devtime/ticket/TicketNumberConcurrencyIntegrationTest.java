package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.TicketScenario;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Atomicidade da sequência de {@code number} (RN-302, CA-02 de specs/007-tickets).
 *
 * <p>Este é o teste que a spec exige escrito <b>antes</b> do gerador (T-007-04). A geração de
 * número é a única operação da feature que falha <b>silenciosamente</b> sob concorrência: uma
 * implementação {@code MAX + 1} passa em ambiente sequencial e só quebra em produção, na forma de
 * duas chaves iguais comunicadas ao mesmo cliente (R-01, CP-03).
 */
class TicketNumberConcurrencyIntegrationTest extends FeatureTestSupport {

    /** CA-02 da spec: 100 criações simultâneas no mesmo contrato. */
    private static final int CONCURRENT_CREATIONS = 100;

    @Autowired private TicketService ticketService;
    @Autowired private TicketScenario scenario;

    @Test
    @DisplayName(
            "RN-302/CA-02: 100 criações simultâneas produzem 100 números distintos e consecutivos")
    void concurrentCreationsShouldProduceDistinctConsecutiveNumbers() throws Exception {
        ContractResponse contract =
                asOwnerOfA(() -> scenario.activeContract(scenario.activeClient()));

        List<Callable<TicketResponse>> creations =
                IntStream.range(0, CONCURRENT_CREATIONS)
                        .<Callable<TicketResponse>>mapToObj(
                                index ->
                                        () ->
                                                asOwnerOfA(
                                                        () ->
                                                                ticketService.create(
                                                                        request(
                                                                                contract.id(),
                                                                                index))))
                        .toList();

        List<Integer> numbers;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<TicketResponse>> futures = pool.invokeAll(creations);
            numbers = new java.util.ArrayList<>();
            for (Future<TicketResponse> future : futures) {
                numbers.add(future.get().number());
            }
        }

        assertThat(numbers)
                .as("INV-TCK-01: nenhum número se repete dentro do contrato")
                .doesNotHaveDuplicates()
                .hasSize(CONCURRENT_CREATIONS);
        assertThat(numbers.stream().sorted().toList())
                .as("CX-02: a sequência não deixa lacunas quando toda criação é bem-sucedida")
                .isEqualTo(IntStream.rangeClosed(1, CONCURRENT_CREATIONS).boxed().toList());
    }

    private TicketCreateRequest request(UUID contractId, int index) {
        return new TicketCreateRequest(
                contractId,
                "Ticket concorrente " + index,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
