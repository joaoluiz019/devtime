package com.devtime.contract;

import com.devtime.contract.dto.BalanceRequests.AdjustmentRequest;
import com.devtime.contract.dto.BalanceResponses.AdjustmentResponse;
import java.util.List;
import java.util.UUID;

/**
 * Ajustes manuais de saldo (RN-215, RN-235 a RN-238).
 *
 * <p>Existe {@code apply} e {@code list} — e mais nada. <b>Não há edição nem exclusão</b> (RN-236,
 * INV-ADJ-01): a ausência dos métodos é a garantia, do mesmo modo que a ausência das rotas. Um
 * ajuste errado é corrigido por um estorno, que fica visível no extrato que o cliente lê — e é
 * assim que deve ser, porque o ajuste original já influenciou um saldo que alguém consultou.
 */
public interface AdjustmentService {

    /**
     * RN-238: apenas {@code ADMIN} e {@code OWNER}.
     *
     * <p>Conceder ou retirar horas contratadas é decisão de quem responde comercialmente pelo
     * tenant; {@code MANAGER} gerencia entrega, não apuração financeira (§7 de permissions.md).
     */
    AdjustmentResponse apply(UUID periodId, AdjustmentRequest request);

    /** Extrato de ajustes do período, em ordem cronológica. */
    List<AdjustmentResponse> listByPeriod(UUID periodId);

    /**
     * RN-230: ajuste automático de expiração de saldo transportado.
     *
     * <p>Interface interna para {@code RolloverExpiryJob}. Usa {@code reason = OTHER} com a
     * justificativa normativa e {@code actorType = SYSTEM} na auditoria — o débito precisa aparecer
     * no extrato como qualquer outro, ou o cliente veria o saldo cair sem explicação.
     */
    AdjustmentResponse applySystemExpiry(UUID periodId, int minutes, String justification);
}
