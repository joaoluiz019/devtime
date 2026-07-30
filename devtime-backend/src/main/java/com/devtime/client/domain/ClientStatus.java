package com.devtime.client.domain;

/**
 * Situação do cliente (state-machines.md §4.4).
 *
 * <p>Apenas dois estados, com transição em ambos os sentidos. {@code INACTIVE} bloqueia a criação
 * de novos contratos (RN-405) e <b>não</b> afeta os existentes (RN-407, CE-C-04).
 */
public enum ClientStatus {
    ACTIVE,
    INACTIVE
}
