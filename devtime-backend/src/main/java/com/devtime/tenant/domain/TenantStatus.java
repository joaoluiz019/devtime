package com.devtime.tenant.domain;

/** Estados de um {@link Tenant} (glossário §9, state-machines.md). */
public enum TenantStatus {
    ACTIVE,
    /** Apenas leitura: escrita retorna {@code 403 DEVTIME-1201} (§10 da constituição). */
    SUSPENDED,
    /** INV-TEN-04: não aceita nenhuma escrita. Purga após 30 dias (RN-008). */
    CANCELLED
}
