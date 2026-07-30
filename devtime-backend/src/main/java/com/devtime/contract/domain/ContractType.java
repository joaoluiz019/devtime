package com.devtime.contract.domain;

/**
 * Modelo comercial do contrato (entities.md §6.6).
 *
 * <p>Imutável após o contrato sair de {@code DRAFT} (RN-206): mudar o modelo invalidaria todo o
 * histórico de saldo já apurado.
 */
public enum ContractType {
    /** Pacote mensal de horas, com saldo, carry-over e alerta de consumo. */
    MONTHLY_HOURS,
    /** Horas abertas: sem teto por definição, logo sem saldo nem alerta (RN-210). */
    HOURLY_OPEN
}
