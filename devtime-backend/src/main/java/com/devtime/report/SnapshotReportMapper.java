package com.devtime.report;

import com.devtime.report.ReportDataResolver.FromSnapshot;
import com.devtime.report.dto.ReportResponses.ReportAdjustment;
import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportClient;
import com.devtime.report.dto.ReportResponses.ReportContract;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportIssuer;
import com.devtime.report.dto.ReportResponses.ReportPeriod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Payload congelado → conteúdo do relatório (RN-701, §24).
 *
 * <p><b>Lê exclusivamente do payload, nunca das entidades atuais.</b> É o que torna o relatório de
 * período fechado imutável no tempo (ART-005, INV-RPT-01): o nome do cliente, o código do contrato,
 * a categoria de cada linha e o nome de quem trabalhou vêm do que foi gravado no fechamento. Uma
 * correção cadastral posterior — mesmo legítima, mesmo de um nome digitado errado — não alcança
 * este documento, e OB-01 registra que isso é contraintuitivo e correto.
 *
 * <p>Produz o <b>mesmo</b> {@link ReportBody} que {@link LiveReportMapper}, e é por isso que o
 * frontend não precisa saber de onde o dado veio — apenas se é parcial ou definitivo.
 */
@Component
@RequiredArgsConstructor
public class SnapshotReportMapper {

    private final ObjectMapper objectMapper;
    private final ReportEntryMapper entryMapper;
    private final ReportFinancialCalculator financialCalculator;

    /**
     * @param includeFinancial resultado da permissão e da opção do pedido, já resolvido pelo
     *     serviço; falso omite o bloco monetário e os valores das linhas (CP-03, CP-08)
     * @param restrictToUserId escopo já resolvido por {@code ReportScopePolicy}; aplicado também
     *     aqui, e não só na consulta ao vivo, porque INV-RPT-04 não abre exceção para período
     *     fechado — um {@code MEMBER} continua sem ver linha de terceiro no snapshot
     * @param billableOnly filtro de faturabilidade do pedido; nulo inclui os dois (CP-05)
     */
    public ReportBody map(
            FromSnapshot source,
            boolean includeFinancial,
            UUID restrictToUserId,
            Boolean billableOnly) {
        JsonNode payload = parse(source.payload());
        JsonNode contract = payload.path("contract");

        BigDecimal hourlyRate = includeFinancial ? decimal(contract, "hourlyRate") : null;
        BigDecimal overageRate = includeFinancial ? decimal(contract, "overageRate") : null;

        List<ReportEntry> entries = entries(payload, hourlyRate, restrictToUserId, billableOnly);
        ReportBalance balance = balance(payload.path("totals"));
        String currency = text(payload.path("period"), "currency");

        return new ReportBody(
                issuer(payload.path("issuer")),
                client(payload.path("client")),
                contract(contract),
                period(payload.path("period"), source),
                balance,
                adjustments(payload),
                financialCalculator.compose(balance, hourlyRate, overageRate, currency),
                entries,
                instant(payload, "snapshotAt"),
                payload.path("period").path("reopenCount").asInt(),
                currency);
    }

    /** O instante do congelamento, exibido como {@code snapshotAt} em §6. */
    public Instant snapshotAt(String payload) {
        return instant(parse(payload), "snapshotAt");
    }

    /**
     * Linhas do payload, filtradas pelo que o payload é capaz de responder.
     *
     * <p><b>Lacuna reportada.</b> Os filtros por categoria, etiqueta e — no caso de vários usuários
     * — por conjunto de usuários <b>não</b> se aplicam a período fechado: o payload congela
     * <b>rótulos</b>, não chaves (é o que torna o snapshot legível depois de a categoria ser
     * renomeada, CX-03), e casar identificador com rótulo exigiria ler as tabelas atuais — que é
     * exatamente o que RN-701 proíbe. Nenhum documento trata o caso. Os filtros são ignorados em
     * vez de recusados, porque recusar tornaria o relatório mais importante do produto inacessível
     * a partir de uma tela cujos filtros vêm preenchidos.
     *
     * <p>O escopo do solicitante <b>é</b> aplicado, porque o payload guarda {@code userId}: uma
     * restrição de segurança que se dissolvesse ao fechar o período seria uma regressão de
     * isolamento no exato momento em que o documento passa a circular.
     */
    private List<ReportEntry> entries(
            JsonNode payload, BigDecimal hourlyRate, UUID restrictToUserId, Boolean billableOnly) {
        JsonNode workLogs = payload.path("workLogs");
        if (!workLogs.isArray()) {
            return List.of();
        }
        List<ReportEntry> entries = new ArrayList<>(workLogs.size());
        // A ordem do array é a que o fechamento gravou, e ela é a ordem normativa de §6.3 aplicada
        // por `findByPeriodForStatement`. Reordenar aqui — mesmo pelo mesmo critério — abriria a
        // possibilidade de dois relatórios do mesmo período divergirem na ordem (RN-708).
        workLogs.forEach(
                node -> {
                    if (keeps(node, restrictToUserId, billableOnly)) {
                        entries.add(entryMapper.fromSnapshot(node, hourlyRate));
                    }
                });
        return List.copyOf(entries);
    }

    private boolean keeps(JsonNode node, UUID restrictToUserId, Boolean billableOnly) {
        if (restrictToUserId != null && !restrictToUserId.toString().equals(text(node, "userId"))) {
            return false;
        }
        return billableOnly == null || billableOnly == node.path("billable").asBoolean(false);
    }

    private ReportIssuer issuer(JsonNode node) {
        return new ReportIssuer(
                text(node, "name"),
                text(node, "legalName"),
                text(node, "documentNumber"),
                text(node, "email"),
                text(node, "phone"),
                text(node, "logoUrl"),
                // O endereço não foi congelado na versão 2 do payload — a lacuna está registrada
                // no CHANGELOG desta sprint. Nulo é honesto; buscá-lo na tabela quebraria RN-701.
                null);
    }

    private ReportClient client(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new ReportClient(
                text(node, "name"), text(node, "legalName"), text(node, "documentNumber"), null);
    }

    private ReportContract contract(JsonNode node) {
        return new ReportContract(
                text(node, "code"),
                text(node, "name"),
                text(node, "type"),
                node.hasNonNull("monthlyMinutes") ? node.get("monthlyMinutes").asInt() : null);
    }

    private ReportPeriod period(JsonNode node, FromSnapshot source) {
        return new ReportPeriod(
                text(node, "label"),
                node.path("sequence").asInt(),
                localDate(node, "startDate"),
                localDate(node, "endDate"),
                // O payload não guarda o status: no instante do congelamento ele é sempre CLOSED, e
                // gravá-lo seria gravar uma constante. Vem da entidade, que é o único campo desta
                // resposta que não vem do payload — e o único que RN-701 não precisa congelar.
                source.period().status());
    }

    private ReportBalance balance(JsonNode totals) {
        return new ReportBalance(
                totals.path("contractedMinutes").asInt(),
                totals.path("carriedInMinutes").asInt(),
                totals.path("adjustmentMinutes").asInt(),
                totals.path("availableMinutes").asInt(),
                totals.path("consumedMinutes").asInt(),
                totals.path("nonBillableMinutes").asInt(),
                totals.path("remainingMinutes").asInt(),
                totals.path("overageMinutes").asInt(),
                totals.path("carriedOutMinutes").asInt(),
                decimal(totals, "consumptionRate"));
    }

    /** CP-06: listados individualmente, com a justificativa que os torna defensáveis. */
    private List<ReportAdjustment> adjustments(JsonNode payload) {
        JsonNode array = payload.path("adjustments");
        if (!array.isArray()) {
            return List.of();
        }
        List<ReportAdjustment> adjustments = new ArrayList<>(array.size());
        array.forEach(
                node ->
                        adjustments.add(
                                new ReportAdjustment(
                                        node.path("minutes").asInt(),
                                        text(node, "reason"),
                                        text(node, "justification"),
                                        // FR-129 proíbe exibir o identificador do autor; o payload
                                        // guarda o UUID e não o nome, então o campo fica nulo em
                                        // vez de expor a chave. Lacuna registrada no CHANGELOG.
                                        null,
                                        instant(node, "appliedAt"))));
        return List.copyOf(adjustments);
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            // CG-06: um payload ilegível é defeito de integridade, não caso de negócio. Falha alto
            // em vez de devolver um relatório vazio que se apresentaria como definitivo.
            throw new IllegalStateException("Payload de snapshot ilegível", e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDate localDate(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : LocalDate.parse(raw);
    }

    private Instant instant(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : Instant.parse(raw);
    }

    /**
     * ART-040: texto no payload justamente para não perder a escala de 4 casas na ida e na volta.
     */
    private BigDecimal decimal(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
    }
}
