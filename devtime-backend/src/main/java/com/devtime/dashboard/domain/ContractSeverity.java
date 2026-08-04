package com.devtime.dashboard.domain;

/**
 * Escala de severidade do cartão de contrato (§6.2 de specs/010).
 *
 * <p>A ordem de declaração é a ordem de criticidade crescente, e é ela que CP-02 usa para ordenar
 * os cartões — {@link #compareTo} decrescente coloca no topo o que exige ação hoje.
 *
 * <p>Os limiares são <b>do contrato</b> ({@code notificationThresholds}), nunca 50/80/100 fixos
 * (CP-04). Fixá-los faria um contrato configurado com {@code [70, 90]} receber alerta por e-mail em
 * 70% enquanto a tela mostrasse "OK" — dois números para a mesma situação.
 */
public enum ContractSeverity {
    OK,
    INFO,
    WARNING,
    CRITICAL
}
