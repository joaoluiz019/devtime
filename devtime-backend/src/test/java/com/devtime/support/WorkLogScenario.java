package com.devtime.support;

import com.devtime.category.CategoryService;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.contract.ContractPeriodService;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.ticket.TicketService;
import com.devtime.ticket.dto.TicketRequests.TicketCreateRequest;
import com.devtime.ticket.dto.TicketResponses.TicketResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cenário mínimo para exercitar registro de horas e cronômetro.
 *
 * <p>BR-207: tudo nasce pelos serviços de produção. Um {@code INSERT} de setup pularia {@code
 * AuditListener}, geração de UUIDv7 e atribuição de tenant, e o teste passaria a exercitar um
 * objeto que o sistema nunca produziria.
 *
 * <p><b>Sobre as datas:</b> o contrato começa em {@link TicketScenario#CONTRACT_START} (10/01/2026)
 * com {@code billingDay = 1}, então o primeiro período — o único gerado até a ativação — vai de
 * 10/01 a 31/01/2026 (RN-211). O relógio dos testes está fixo em 29/07/2026, o que torna qualquer
 * data de janeiro <b>retroativa além da janela padrão de 30 dias</b>: os testes rodam como {@code
 * OWNER}, que é justamente o papel autorizado por RN-120 a lançar fora dela.
 */
@Component
@RequiredArgsConstructor
public class WorkLogScenario {

    /** Fuso dos tenants criados por {@link FoundationDataBuilder}. */
    public static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Dia dentro do primeiro período do contrato (10/01 a 31/01/2026). */
    public static final LocalDate WORK_DAY = LocalDate.of(2026, 1, 15);

    private final TicketScenario ticketScenario;
    private final TicketService ticketService;
    private final CategoryService categoryService;
    private final ContractPeriodService contractPeriodService;

    /** Cliente ativo, contrato ativo com primeiro período aberto, ticket e categoria. */
    public record Scenario(
            UUID clientId,
            ContractResponse contract,
            TicketResponse ticket,
            CategoryResponse category,
            ContractPeriodResponse period) {}

    public Scenario create() {
        UUID clientId = ticketScenario.activeClient();
        ContractResponse contract = ticketScenario.activeContract(clientId);
        return withContract(clientId, contract);
    }

    public Scenario withContract(UUID clientId, ContractResponse contract) {
        categoryService.seedDefaults(); // RN-501: idempotente (CX-14 de 005)
        CategoryResponse category = categoryService.listActive().get(0);
        TicketResponse ticket =
                ticketService.create(
                        new TicketCreateRequest(
                                contract.id(),
                                "Ticket de horas",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                List.of(),
                                null));
        ContractPeriodResponse period =
                contractPeriodService.resolveOpenPeriod(contract.id(), WORK_DAY).orElseThrow();
        return new Scenario(clientId, contract, ticket, category, period);
    }

    /**
     * Instante no fuso do tenant, dentro do primeiro período.
     *
     * <p>Os testes escrevem horários locais porque é assim que as regras estão redigidas — RN-108
     * fala em "data local de {@code startedAt}" — e converter aqui mantém a conversão em um lugar
     * só.
     */
    public static Instant at(int hour, int minute) {
        return at(WORK_DAY, hour, minute, 0);
    }

    public static Instant at(int hour, int minute, int second) {
        return at(WORK_DAY, hour, minute, second);
    }

    public static Instant at(LocalDate day, int hour, int minute, int second) {
        return day.atTime(LocalTime.of(hour, minute, second)).atZone(TENANT_ZONE).toInstant();
    }
}
