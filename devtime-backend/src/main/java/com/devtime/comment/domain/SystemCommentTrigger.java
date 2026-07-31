package com.devtime.comment.domain;

/**
 * Gatilhos de comentário de sistema (RN-815).
 *
 * <p>Exatamente três — mudança de situação, alteração de responsável e alteração de contrato. São
 * as mudanças <b>estruturais</b> do ticket: alterar título ou prioridade não gera comentário,
 * porque a trilha de auditoria já registra a edição e um comentário por campo alterado
 * transformaria a conversa em log.
 */
public enum SystemCommentTrigger {

    /** Transição de situação, manual ou automática (RN-312). */
    STATUS_CHANGED,

    /** Atribuição, reatribuição ou remoção de responsável. */
    ASSIGNEE_CHANGED,

    /** Movimentação entre contratos do mesmo cliente (RN-305). */
    CONTRACT_MOVED
}
