package com.devtime.worklog.domain;

import java.time.Instant;

/**
 * Intervalo de trabalho semi-aberto {@code [startedAt, endedAt)} (entities.md §7.2, BR-148).
 *
 * <p>A definição de sobreposição de RN-102 mora aqui, e não no detector, porque é uma propriedade
 * do intervalo — não da consulta. Isso a torna verificável por teste puro contra a tabela normativa
 * de §6.2, sem banco, e dá à consulta SQL de {@code WorkLogRepository.existsOverlapping} um oráculo
 * ao qual ela precisa corresponder.
 *
 * <p><b>Por que semi-aberto</b> (OB-01): com intervalos fechados, uma sessão terminando às 11:00 e
 * outra começando às 11:00 seriam sobrepostas — rejeitando o caso mais comum do mundo real, que é
 * encerrar uma tarefa e começar a seguinte. Difere deliberadamente de {@link
 * com.devtime.shared.time.DateRange}, que é fechado: um período de contrato que termina em 31/08
 * inclui o dia 31 inteiro.
 *
 * @param startedAt início, inclusive
 * @param endedAt fim, exclusive
 */
public record WorkLogInterval(Instant startedAt, Instant endedAt) {

    public WorkLogInterval {
        if (startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("WorkLogInterval exige startedAt e endedAt");
        }
    }

    /**
     * RN-102: {@code A.startedAt < B.endedAt} <b>e</b> {@code B.startedAt < A.endedAt}.
     *
     * <p>A comparação é <b>estrita nos dois lados</b>. Trocar qualquer um dos {@code <} por {@code
     * <=} rejeitaria sessões consecutivas legítimas; trocar por {@code >} ou inverter os operandos
     * deixaria passar sobreposições reais. As duas falhas são invisíveis em revisão e visíveis
     * apenas na fatura do cliente.
     */
    public boolean overlaps(WorkLogInterval other) {
        return startedAt.isBefore(other.endedAt()) && other.startedAt().isBefore(endedAt);
    }
}
