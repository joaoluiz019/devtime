package com.devtime.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Registro da trilha de auditoria (RN-006).
 *
 * <p>Interface pública consumida pelas features que mantêm entidades auditáveis — {@code Client},
 * {@code Contract} e {@code ContractPeriod} nesta sprint (entities.md §6.20).
 *
 * <p>O registro ocorre na <b>mesma transação</b> da alteração: uma trilha que pode divergir do dado
 * não tem valor em disputa contratual, que é a razão de ART-003 exigi-la.
 */
public interface AuditService {

    /**
     * Registra uma alteração.
     *
     * @param action verbo no passado, ex.: {@code CONTRACT_CREATED} (entities.md §6.20)
     * @param entityType nome da entidade auditada
     * @param entityId identificador da entidade
     * @param beforeState apenas os campos alterados; vazio em criação
     * @param afterState apenas os campos alterados; vazio em exclusão
     */
    void record(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState);

    /**
     * Registra uma alteração executada pelo sistema, sem usuário (CE-S-06).
     *
     * <p>Usado por geração automática de períodos, em que o ator é um job e não uma pessoa.
     */
    void recordSystemAction(
            String action, String entityType, UUID entityId, Map<String, Object> afterState);
}
