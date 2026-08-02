package com.devtime.shared.antivirus;

import java.io.InputStream;

/**
 * Porta de saída do verificador antivírus (integrations.md §6.3).
 *
 * <p>RS-03 torna esta dependência <b>obrigatória</b>: sem ela, nenhum download é liberado. AV-02 é
 * a regra que dá forma ao contrato — falha ou indisponibilidade do verificador <b>nunca</b> libera
 * o arquivo. Por isso {@link ScanVerdict#FAILED} existe como resultado explícito em vez de exceção:
 * a indisponibilidade é um estado previsto da máquina de §4.9, não um erro a propagar.
 */
public interface AntivirusPort {

    /**
     * Verifica o conteúdo.
     *
     * <p>O fluxo é consumido integralmente e <b>nunca</b> carregado em memória pelo adapter
     * (CP-14): um arquivo de 10 MB por verificação concorrente esgotaria o heap.
     *
     * @return {@link ScanVerdict#CLEAN}, {@link ScanVerdict#INFECTED} com a ameaça identificada, ou
     *     {@link ScanVerdict#FAILED} em qualquer erro ou indisponibilidade. Nunca lança.
     */
    ScanResult scan(InputStream content);

    /** Resultado da verificação. */
    enum ScanVerdict {
        CLEAN,
        INFECTED,
        FAILED
    }

    /**
     * @param verdict resultado
     * @param threat ameaça identificada; preenchida apenas em {@link ScanVerdict#INFECTED}. É o
     *     dado que §18 exige na trilha de {@code ATTACHMENT_SCAN_INFECTED} e a base de qualquer
     *     investigação de segurança
     * @param failureReason causa da falha; preenchida apenas em {@link ScanVerdict#FAILED}
     */
    record ScanResult(ScanVerdict verdict, String threat, String failureReason) {

        public static ScanResult clean() {
            return new ScanResult(ScanVerdict.CLEAN, null, null);
        }

        public static ScanResult infected(String threat) {
            return new ScanResult(ScanVerdict.INFECTED, threat, null);
        }

        public static ScanResult failed(String reason) {
            return new ScanResult(ScanVerdict.FAILED, null, reason);
        }
    }
}
