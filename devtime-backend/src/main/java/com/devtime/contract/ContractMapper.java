package com.devtime.contract;

import com.devtime.contract.domain.Contract;
import com.devtime.contract.domain.ContractPeriod;
import com.devtime.contract.domain.PeriodPlan;
import com.devtime.contract.dto.ContractResponses.ContractPeriodResponse;
import com.devtime.contract.dto.ContractResponses.ContractResponse;
import com.devtime.contract.dto.ContractResponses.PeriodPreviewItem;
import java.util.Arrays;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão das entidades de contrato para DTO (ADR-014, BR-104).
 *
 * <p>Os campos derivados de contexto — cliente, período corrente, prévia, transições e ações
 * disponíveis — são ignorados aqui e preenchidos pelo serviço. BR-105 proíbe que o mapper contenha
 * regra de negócio, e decidir quais transições estão disponíveis (ME-06) ou omitir campos
 * monetários por permissão (SG-03) é exatamente isso.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ContractMapper {

    @Mapping(target = "client", ignore = true)
    @Mapping(target = "currentPeriod", ignore = true)
    @Mapping(target = "periodsPreview", ignore = true)
    @Mapping(target = "availableTransitions", ignore = true)
    @Mapping(target = "availableActions", ignore = true)
    @Mapping(target = "notificationThresholds", expression = "java(toIntegerList(contract))")
    ContractResponse toResponse(Contract contract);

    ContractPeriodResponse toPeriodResponse(ContractPeriod period);

    List<ContractPeriodResponse> toPeriodResponses(List<ContractPeriod> periods);

    @Mapping(target = "prorated", source = "partial")
    @Mapping(target = "prorationBasis", expression = "java(plan.prorationBasis())")
    PeriodPreviewItem toPreviewItem(PeriodPlan plan);

    List<PeriodPreviewItem> toPreviewItems(List<PeriodPlan> plans);

    /**
     * {@code SMALLINT[]} é mapeado como {@code Short[]} por Hibernate, mas o contrato da API expõe
     * percentuais como inteiros — converter aqui evita vazar o tipo do banco para o JSON.
     */
    default List<Integer> toIntegerList(Contract contract) {
        Short[] thresholds = contract.getNotificationThresholds();
        if (thresholds == null) {
            return List.of();
        }
        return Arrays.stream(thresholds).map(Short::intValue).toList();
    }
}
