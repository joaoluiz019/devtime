package com.devtime.audit.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Exceções da consulta à trilha (spec 002 §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra.
 */
public final class AuditExceptions {

    private AuditExceptions() {}

    /**
     * {@code users.md} §10.1: intervalo de consulta acima de 90 dias.
     *
     * <p>Rejeitar é preferível a truncar silenciosamente: um cliente que pede cinco anos e recebe
     * noventa dias sem aviso conclui que não houve atividade no restante do período.
     */
    public static BusinessRuleException rangeTooWide(long requestedDays, long maxDays) {
        return new AuditRangeTooWideException(requestedDays, maxDays);
    }

    /** {@code DEVTIME-3001} / 400. */
    public static final class AuditRangeTooWideException extends BusinessRuleException {
        private AuditRangeTooWideException(long requestedDays, long maxDays) {
            super(
                    ErrorCode.DATE_RANGE_EXCEEDED,
                    Map.of("requestedDays", requestedDays, "maxDays", maxDays),
                    "Intervalo de consulta acima do máximo permitido");
        }
    }
}
