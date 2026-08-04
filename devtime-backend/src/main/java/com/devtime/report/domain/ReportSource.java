package com.devtime.report.domain;

/**
 * De onde o relatório leu os números (campo {@code source} de §6 de reports.md).
 *
 * <p>É a decisão mais consequente da feature (RN-701, RN-702, OB-01) e por isso aparece na
 * resposta: o cliente do relatório precisa saber se está olhando um documento definitivo ou um
 * número em evolução, e derivar isso do status do período obrigaria cada consumidor a repetir a
 * matriz de §6.1.
 */
public enum ReportSource {

    /**
     * Período {@code CLOSED}: payload congelado no fechamento.
     *
     * <p>Ignora integralmente o estado atual do banco — inclusive correções legítimas de cadastro
     * (OB-01). O documento entregue ao cliente não muda retroativamente (ART-005).
     */
    SNAPSHOT,

    /** Período aberto, reaberto ou intervalo livre: agregação sobre as tabelas. Sempre parcial. */
    LIVE
}
