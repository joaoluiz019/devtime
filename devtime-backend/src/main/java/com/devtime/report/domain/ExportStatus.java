package com.devtime.report.domain;

/**
 * Máquina de estados de {@code ReportExecution} (§4.10 de state-machines.md, §10 de specs/012).
 *
 * <pre>
 *   [*] → QUEUED → PROCESSING → COMPLETED → EXPIRED
 *          ↓            ↓
 *      cancelada     FAILED → QUEUED (até 2 tentativas)
 * </pre>
 *
 * <p>Exportação síncrona nasce direto em {@link #COMPLETED}: ela nunca esteve na fila, e registrar
 * uma passagem por {@code QUEUED} que não aconteceu tornaria a trilha de §18 uma ficção.
 */
public enum ExportStatus {

    /** Aguardando o worker. Só aqui o cancelamento é permitido (§11.1, CP-15). */
    QUEUED,

    /** O worker assumiu. Cancelar exigiria interrompê-lo no meio — proibido por CP-15. */
    PROCESSING,

    /** Arquivo gravado no storage. INV-RPT-05: {@code storageKey} nunca é nulo aqui. */
    COMPLETED,

    /** Erro de geração, com o motivo registrado. Até 2 tentativas no total (CP-16). */
    FAILED,

    /**
     * Sete dias após a geração; o binário foi <b>removido</b> do storage, não só marcado (SG-09).
     */
    EXPIRED;

    /** §10: baixar exige o arquivo pronto. */
    public boolean isDownloadable() {
        return this == COMPLETED;
    }

    /** §11.1: cancelar é permitido apenas antes de o worker assumir. */
    public boolean isCancellable() {
        return this == QUEUED;
    }

    /** §4.10: a fila do {@code ExportProcessorJob} são os dois estados que o worker assume. */
    public boolean isPendingWork() {
        return this == QUEUED || this == FAILED;
    }
}
