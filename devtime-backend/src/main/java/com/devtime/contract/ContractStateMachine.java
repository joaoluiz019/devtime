package com.devtime.contract;

import com.devtime.contract.domain.ContractExceptions;
import com.devtime.contract.domain.ContractStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Matriz de transições do contrato (state-machines.md §4.5).
 *
 * <p>BR-071/BR-072: nenhuma mudança de situação ocorre por {@code set} direto no status, e toda
 * guarda é verificada antes de qualquer efeito. A matriz é declarada uma única vez aqui para que
 * "quais transições existem" tenha uma resposta só — duplicá-la no serviço e na interface
 * produziria divergência silenciosa.
 *
 * <p>As guardas específicas de cada transição (cliente ativo, ausência de cronômetro,
 * justificativa) pertencem ao serviço: dependem de dados que esta classe deliberadamente não
 * acessa.
 */
@Component
public class ContractStateMachine {

    private static final Map<ContractStatus, Set<ContractStatus>> TRANSITIONS = buildMatrix();

    public boolean canTransition(ContractStatus from, ContractStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** ME-06: usado por {@code availableActions} para não oferecer ação que seria rejeitada. */
    public Set<ContractStatus> availableTransitions(ContractStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2010} / {@code 409},
     *     com {@code availableTransitions} no corpo (EX-09)
     */
    public void assertCanTransition(ContractStatus from, ContractStatus to) {
        if (!canTransition(from, to)) {
            throw ContractExceptions.invalidTransition(from, to, availableTransitions(from));
        }
    }

    private static Map<ContractStatus, Set<ContractStatus>> buildMatrix() {
        Map<ContractStatus, Set<ContractStatus>> matrix = new EnumMap<>(ContractStatus.class);
        // DRAFT nunca vai a SUSPENDED nem ENDED: não se suspende nem se encerra o que não vigorou.
        matrix.put(ContractStatus.DRAFT, Set.of(ContractStatus.ACTIVE, ContractStatus.CANCELLED));
        matrix.put(
                ContractStatus.ACTIVE,
                Set.of(ContractStatus.SUSPENDED, ContractStatus.ENDED, ContractStatus.CANCELLED));
        matrix.put(
                ContractStatus.SUSPENDED,
                Set.of(ContractStatus.ACTIVE, ContractStatus.ENDED, ContractStatus.CANCELLED));
        // CE-15: ENDED e CANCELLED são terminais. Reativar recriaria a sequência de períodos com
        // lacuna temporal, quebrando INV-PER-03 — o caminho é criar um novo contrato.
        matrix.put(ContractStatus.ENDED, Set.of());
        matrix.put(ContractStatus.CANCELLED, Set.of());
        return Map.copyOf(matrix);
    }
}
