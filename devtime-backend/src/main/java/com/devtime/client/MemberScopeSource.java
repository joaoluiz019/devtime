package com.devtime.client;

import java.util.Set;
import java.util.UUID;

/**
 * Origem do vínculo entre um membro e clientes (permissions.md §9, nota ²).
 *
 * <p>A definição operacional de "cliente vinculado" depende de features que <b>não</b> podem ser
 * alcançadas daqui: {@code contracts}, {@code tickets} e, futuramente, {@code work_logs}. AR-02
 * proíbe que {@code client} dependa delas — e a dependência natural é a inversa, já que {@code
 * contract} depende de {@code client}.
 *
 * <p>A inversão resolve: {@code client} declara o contrato, as features que <b>possuem</b> a
 * informação de vínculo o implementam, e o Spring injeta as disponíveis. Uma feature ausente
 * simplesmente não contribui vínculos — e o escopo permanece fechado por padrão (ART-085), que é o
 * comportamento correto enquanto a informação não existe.
 */
public interface MemberScopeSource {

    /**
     * Clientes aos quais o membro está vinculado.
     *
     * @return conjunto possivelmente vazio; nunca {@code null} (ER-06)
     */
    Set<UUID> linkedClientIdsOf(UUID userId);
}
