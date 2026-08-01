package com.devtime.contract;

import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodAdjustment;
import com.devtime.contract.domain.PeriodBalance;
import com.devtime.contract.domain.PeriodSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Montagem do snapshot de fechamento (entities.md §6.9, RN-241 passo 4, RN-245).
 *
 * <p>O payload é <b>canônico</b>: as chaves são serializadas em ordem determinística ({@code
 * ORDER_MAP_ENTRIES_BY_KEYS} sobre mapas ordenados). Sem isso, o mesmo conteúdo produziria
 * checksums diferentes entre execuções e a verificação de integridade (SG-05, CX-21) acusaria
 * adulteração onde só houve reserialização.
 *
 * <p>O checksum é SHA-256 sobre exatamente os bytes persistidos em {@code payload} — não sobre um
 * objeto intermediário. Verificar depois é reler a coluna e recalcular; qualquer outra combinação
 * compararia coisas diferentes.
 *
 * <p>RN-245: o excedente é gravado no snapshot. Ele é o número que sustenta a cobrança adicional do
 * período, e precisa ficar congelado junto com o restante.
 */
@Component
@RequiredArgsConstructor
public class SnapshotBuilder {

    /** Versão do formato do payload; incrementada quando a estrutura mudar (entities.md §6.9). */
    public static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    /** Item de work log congelado no snapshot — dados suficientes para reemitir o relatório. */
    public record SnapshotWorkLog(
            String id,
            String workDate,
            String ticketKey,
            String categoryName,
            String userId,
            String description,
            int netMinutes,
            int billableMinutes) {}

    public PeriodSnapshot build(
            ContractPeriod period,
            PeriodBalance balance,
            int carriedOutMinutes,
            List<SnapshotWorkLog> workLogs,
            List<PeriodAdjustment> adjustments,
            Instant snapshotAt) {
        String payload =
                serialize(period, balance, carriedOutMinutes, workLogs, adjustments, snapshotAt);

        PeriodSnapshot snapshot = new PeriodSnapshot();
        snapshot.setContractPeriodId(period.getId());
        snapshot.setSnapshotAt(snapshotAt);
        snapshot.setPayload(payload);
        snapshot.setChecksum(checksum(payload));
        snapshot.setSchemaVersion(SCHEMA_VERSION);
        return snapshot;
    }

    /** SHA-256 hexadecimal minúsculo dos bytes UTF-8 do payload. */
    public String checksum(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            // SHA-256 é obrigatório em toda JVM; a ausência é falha de plataforma, não condição
            // de negócio recuperável (CG-06).
            throw new IllegalStateException("SHA-256 indisponível na plataforma", unavailable);
        }
    }

    private String serialize(
            ContractPeriod period,
            PeriodBalance balance,
            int carriedOutMinutes,
            List<SnapshotWorkLog> workLogs,
            List<PeriodAdjustment> adjustments,
            Instant snapshotAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("snapshotAt", snapshotAt.toString());
        payload.put("tenantId", period.getTenantId().toString());
        payload.put("contractId", period.getContractId().toString());
        payload.put("period", periodBlock(period, carriedOutMinutes));
        payload.put("totals", totalsBlock(balance, carriedOutMinutes));
        payload.put("workLogs", workLogs);
        payload.put("adjustments", adjustments.stream().map(this::adjustmentBlock).toList());

        try {
            return objectMapper
                    .copy()
                    // Determinismo do payload: sem ordenação estável, dois fechamentos idênticos
                    // gerariam checksums diferentes.
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Falha ao serializar o snapshot do período", failure);
        }
    }

    private Map<String, Object> periodBlock(ContractPeriod period, int carriedOutMinutes) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", period.getId().toString());
        block.put("sequence", period.getSequence());
        block.put("label", period.getLabel());
        block.put("startDate", period.getStartDate().toString());
        block.put("endDate", period.getEndDate().toString());
        block.put("currency", period.getCurrency());
        block.put("reopenCount", period.getReopenCount());
        block.put("carriedOutMinutes", carriedOutMinutes);
        return block;
    }

    private Map<String, Object> totalsBlock(PeriodBalance balance, int carriedOutMinutes) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("contractedMinutes", balance.contractedMinutes());
        block.put("carriedInMinutes", balance.carriedInMinutes());
        block.put("adjustmentMinutes", balance.adjustmentMinutes());
        block.put("availableMinutes", balance.availableMinutes());
        block.put("consumedMinutes", balance.consumedMinutes());
        block.put("nonBillableMinutes", balance.nonBillableMinutes());
        block.put("remainingMinutes", balance.remainingMinutes());
        // RN-245.
        block.put("overageMinutes", balance.overageMinutes());
        block.put("consumptionRate", balance.consumptionRate().toPlainString());
        block.put("carriedOutMinutes", carriedOutMinutes);
        return block;
    }

    private Map<String, Object> adjustmentBlock(PeriodAdjustment adjustment) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", adjustment.getId().toString());
        block.put("minutes", adjustment.getMinutes());
        block.put("reason", adjustment.getReason().name());
        block.put("justification", adjustment.getJustification());
        block.put("appliedBy", String.valueOf(adjustment.getAppliedBy()));
        block.put("appliedAt", adjustment.getAppliedAt().toString());
        return block;
    }
}
