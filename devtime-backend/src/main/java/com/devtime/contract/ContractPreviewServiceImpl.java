package com.devtime.contract;

import com.devtime.contract.domain.PeriodSpec;
import com.devtime.contract.dto.ContractRequests.PeriodPreviewRequest;
import com.devtime.contract.dto.ContractResponses.PeriodPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cálculo da prévia (contracts.md §6).
 *
 * <p>{@code readOnly = true} e nenhum acesso a repositório: a prévia é cálculo puro. Usa o mesmo
 * {@link PeriodGenerator} da ativação, o que torna estrutural a garantia de CA-01 — a prévia não
 * pode divergir do que será gerado porque é produzida pelo mesmo código.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractPreviewServiceImpl implements ContractPreviewService {

    private static final int DEFAULT_PERIODS = 3;

    private final PeriodGenerator generator;
    private final ContractTypeCoherenceValidator coherenceValidator;
    private final ContractMapper mapper;

    @Override
    @PreAuthorize("hasPermission(null, 'CONTRACT_CREATE')")
    public PeriodPreviewResponse preview(PeriodPreviewRequest request) {
        coherenceValidator.assertCoherent(
                request.type(),
                request.monthlyMinutes(),
                null,
                null,
                request.billingDay(),
                request.startDate(),
                request.endDate());

        PeriodSpec spec =
                new PeriodSpec(
                        request.type(),
                        request.monthlyMinutes(),
                        request.startDate(),
                        request.endDate(),
                        request.billingDay(),
                        request.prorateFirstPeriod() == null || request.prorateFirstPeriod());

        int count = request.periodsCount() == null ? DEFAULT_PERIODS : request.periodsCount();
        return new PeriodPreviewResponse(mapper.toPreviewItems(generator.generate(spec, count)));
    }
}
