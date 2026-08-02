package com.devtime.attachment.domain;

/**
 * Estado da verificação antivírus (§4.9 de state-machines.md, §6.3 da spec 015).
 *
 * <p>Um único estado libera o download. A pergunta que o código faz é sempre "é {@code CLEAN}?", e
 * nunca "não é {@code INFECTED}?": a segunda forma libera {@code PENDING} e {@code FAILED} por
 * omissão, que é exatamente o modo de falha que RN-803 existe para impedir.
 */
public enum ScanStatus {

    /** Verificação enfileirada. Download bloqueado com {@code 409} (FA-03). */
    PENDING(false),

    /** Único estado que libera o download (RN-803, INV-ATT-02). */
    CLEAN(true),

    /**
     * Ameaça detectada. Binário removido do storage como efeito de entrada (INV-ATT-06); download
     * bloqueado com {@code 403} (FA-04).
     */
    INFECTED(false),

    /**
     * Três tentativas esgotadas. Download bloqueado com {@code 409}, <b>permanentemente</b>.
     *
     * <p>§6.3 e CP-02: não existe caminho de liberação manual. Liberar um arquivo não verificado
     * por decisão administrativa converteria três camadas de defesa em uma caixa de diálogo — e
     * quem clica em "liberar mesmo assim" não tem como avaliar o risco. O usuário reenvia.
     */
    FAILED(false);

    private final boolean downloadable;

    ScanStatus(boolean downloadable) {
        this.downloadable = downloadable;
    }

    /** RN-803: calculado no servidor; o cliente não reimplementa a regra (§23). */
    public boolean isDownloadable() {
        return downloadable;
    }
}
