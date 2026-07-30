package com.devtime.contract;

import com.devtime.contract.dto.ContractRequests.PeriodPreviewRequest;
import com.devtime.contract.dto.ContractResponses.PeriodPreviewResponse;

/**
 * Prévia de períodos sem persistência (contracts.md §6).
 *
 * <p>Existe para que o usuário confira o ciclo e o rateio <b>antes</b> de comprometer o contrato
 * (CA-05 de US-040) e para que a interface atualize a projeção conforme ele digita.
 */
public interface ContractPreviewService {

    PeriodPreviewResponse preview(PeriodPreviewRequest request);
}
