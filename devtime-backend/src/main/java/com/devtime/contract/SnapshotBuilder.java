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
 * <p>O checksum é SHA-256 sobre a <b>forma canônica</b> do payload, e não sobre os bytes gravados:
 * a coluna é {@code JSONB} e o PostgreSQL reordena as chaves ao armazenar, de modo que os bytes de
 * volta nunca são os de ida. Ver {@link #checksum(String)}.
 *
 * <p>RN-245: o excedente é gravado no snapshot. Ele é o número que sustenta a cobrança adicional do
 * período, e precisa ficar congelado junto com o restante.
 */
@Component
@RequiredArgsConstructor
public class SnapshotBuilder {

    /**
     * Versão do formato do payload; incrementada quando a estrutura mudar (entities.md §6.9).
     *
     * <p><b>Versão 2</b> acrescentou os blocos {@code issuer}, {@code client} e {@code contract} e
     * os campos {@code startedAt}, {@code endedAt}, {@code ticketTitle}, {@code userName}, {@code
     * billable} e {@code tags} nas linhas. Sem eles, ADR-036 RP-01 ficava descumprido: o payload
     * guardava o <b>ponteiro</b> ({@code contractId}) e não o <b>valor</b>, de modo que um
     * relatório de período fechado precisaria ler o nome do cliente da tabela — e ele muda. RN-701
     * e ART-005 exigem o contrário.
     *
     * <p>Snapshots gravados na versão 1 <b>não</b> são migrados (RP-03: append-only, imutável). O
     * leitor de {@code 012} devolve os blocos ausentes como vazios, que é a informação honesta: o
     * dado não foi congelado no fechamento e não pode ser reconstruído sem falsificá-lo.
     */
    public static final int SCHEMA_VERSION = 2;

    private final ObjectMapper objectMapper;

    /** Item de work log congelado no snapshot — dados suficientes para reemitir o relatório. */
    public record SnapshotWorkLog(
            String id,
            String workDate,
            String startedAt,
            String endedAt,
            String ticketKey,
            String ticketTitle,
            String categoryName,
            String userId,
            String userName,
            String description,
            int netMinutes,
            int billableMinutes,
            boolean billable,
            List<String> tags) {}

    /**
     * Dados cadastrais congelados no fechamento (ADR-036 RP-01, RN-703).
     *
     * <p>Deliberadamente denormalizado e redundante — é a função do snapshot. {@code hourlyRate} e
     * {@code overageRate} vêm como texto porque {@code BigDecimal} serializado como número perderia
     * a escala de quatro casas de ART-040 na ida e na volta.
     */
    public record SnapshotParties(
            String issuerName,
            String issuerLegalName,
            String issuerDocumentNumber,
            String issuerEmail,
            String issuerPhone,
            String issuerLogoUrl,
            String clientName,
            String clientLegalName,
            String clientDocumentNumber,
            String contractCode,
            String contractName,
            String contractType,
            Integer contractMonthlyMinutes,
            String hourlyRate,
            String overageRate,
            String locale) {}

    public PeriodSnapshot build(
            ContractPeriod period,
            PeriodBalance balance,
            int carriedOutMinutes,
            List<SnapshotWorkLog> workLogs,
            List<PeriodAdjustment> adjustments,
            SnapshotParties parties,
            Instant snapshotAt) {
        String payload =
                serialize(
                        period,
                        balance,
                        carriedOutMinutes,
                        workLogs,
                        adjustments,
                        parties,
                        snapshotAt);

        PeriodSnapshot snapshot = new PeriodSnapshot();
        snapshot.setContractPeriodId(period.getId());
        snapshot.setSnapshotAt(snapshotAt);
        snapshot.setPayload(payload);
        snapshot.setChecksum(checksum(payload));
        snapshot.setSchemaVersion(SCHEMA_VERSION);
        return snapshot;
    }

    /**
     * SHA-256 hexadecimal minúsculo da <b>forma canônica</b> do payload.
     *
     * <p>Não é o hash dos bytes que o Java escreveu. A coluna é {@code JSONB} (V020): o PostgreSQL
     * reparseia o documento, descarta espaço em branco e <b>reordena as chaves</b> pela própria
     * regra de armazenamento. A string relida nunca é, byte a byte, a string gravada — de modo que
     * o hash sobre os bytes fazia <b>todo</b> snapshot ser reportado como adulterado, e o alerta de
     * CX-21 apontaria para corrupção inexistente todas as noites, treinando quem opera a ignorá-lo.
     *
     * <p>Canonicalizar antes de somar resolve na raiz: os dois lados partem do documento lógico e
     * chegam à mesma sequência de bytes, seja qual for a ordem em que cada camada guardou as
     * chaves. É a mesma canonicalização usada na escrita do payload.
     */
    public String checksum(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(canonical(payload).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            // SHA-256 é obrigatório em toda JVM; a ausência é falha de plataforma, não condição
            // de negócio recuperável (CG-06).
            throw new IllegalStateException("SHA-256 indisponível na plataforma", unavailable);
        }
    }

    /**
     * Reserializa o documento com as chaves em ordem estável.
     *
     * <p>Um payload que não é JSON válido não pode ser canonicalizado. Ele é somado como está: o
     * checksum divergente que resulta é exatamente o sinal de adulteração que SG-05 quer emitir —
     * silenciar aqui converteria a detecção em omissão.
     */
    private String canonical(String payload) {
        try {
            ObjectMapper mapper = canonicalMapper();
            // `Object.class`, e não `readTree`: a ordenação de chaves de Jackson vale para `Map`, e
            // um `ObjectNode` preservaria a ordem em que o banco devolveu — que é justamente a que
            // não se pode confiar.
            return mapper.writeValueAsString(mapper.readValue(payload, Object.class));
        } catch (JsonProcessingException naoCanonicalizavel) {
            return payload;
        }
    }

    private ObjectMapper canonicalMapper() {
        return objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private String serialize(
            ContractPeriod period,
            PeriodBalance balance,
            int carriedOutMinutes,
            List<SnapshotWorkLog> workLogs,
            List<PeriodAdjustment> adjustments,
            SnapshotParties parties,
            Instant snapshotAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("snapshotAt", snapshotAt.toString());
        payload.put("tenantId", period.getTenantId().toString());
        payload.put("contractId", period.getContractId().toString());
        payload.put("issuer", issuerBlock(parties));
        payload.put("client", clientBlock(parties));
        payload.put("contract", contractBlock(parties));
        payload.put("period", periodBlock(period, carriedOutMinutes));
        payload.put("totals", totalsBlock(balance, carriedOutMinutes));
        payload.put("workLogs", workLogs);
        payload.put("adjustments", adjustments.stream().map(this::adjustmentBlock).toList());

        try {
            // Determinismo do payload: sem ordenação estável, dois fechamentos idênticos gerariam
            // checksums diferentes.
            return canonicalMapper().writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Falha ao serializar o snapshot do período", failure);
        }
    }

    /**
     * Emissor do documento, congelado (RN-703).
     *
     * <p>{@code LinkedHashMap} e não {@code Map.of}: o payload aceita valor nulo — razão social e
     * documento fiscal são opcionais no cadastro —, e {@code Map.of} rejeita nulo. Omitir a chave
     * quando o valor é nulo produziria payloads com conjuntos de chaves diferentes para o mesmo
     * conteúdo lógico, o que dificulta a leitura no futuro sem ganhar nada.
     */
    private Map<String, Object> issuerBlock(SnapshotParties parties) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("name", parties.issuerName());
        block.put("legalName", parties.issuerLegalName());
        block.put("documentNumber", parties.issuerDocumentNumber());
        block.put("email", parties.issuerEmail());
        block.put("phone", parties.issuerPhone());
        block.put("logoUrl", parties.issuerLogoUrl());
        block.put("locale", parties.locale());
        return block;
    }

    private Map<String, Object> clientBlock(SnapshotParties parties) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("name", parties.clientName());
        block.put("legalName", parties.clientLegalName());
        block.put("documentNumber", parties.clientDocumentNumber());
        return block;
    }

    private Map<String, Object> contractBlock(SnapshotParties parties) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("code", parties.contractCode());
        block.put("name", parties.contractName());
        block.put("type", parties.contractType());
        block.put("monthlyMinutes", parties.contractMonthlyMinutes());
        // Texto, não número: a escala de 4 casas de ART-040 não sobrevive à ida e volta por double.
        block.put("hourlyRate", parties.hourlyRate());
        block.put("overageRate", parties.overageRate());
        return block;
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
