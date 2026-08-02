package com.devtime.attachment;

import java.util.UUID;

/**
 * Verificação antivírus (§4.9 de state-machines.md, spec §22.2).
 *
 * <p>Serviço de <b>sistema</b>: ignora RBAC (CE-P-08 de permissions.md). Não existe endpoint que o
 * alcance, e é isso que garante CP-02 — nenhum caminho de usuário influencia o {@code scanStatus}.
 */
public interface ScanService {

    /**
     * Verifica um anexo e aplica a transição resultante.
     *
     * <p>Convergente e seguro para reexecução (BR-185): um anexo que já saiu de {@code PENDING} ou
     * {@code FAILED} é ignorado.
     *
     * @return o estado resultante, para o job contabilizar
     */
    ScanOutcome scan(UUID attachmentId);

    /** Resultado da tentativa, do ponto de vista do job. */
    enum ScanOutcome {
        CLEAN,
        INFECTED,
        FAILED,
        /** Três tentativas esgotadas; o arquivo é inacessível para sempre (§6.3). */
        EXHAUSTED,
        /** Nada a fazer — já verificado, excluído ou sem binário. */
        SKIPPED
    }
}
