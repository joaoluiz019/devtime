package com.devtime.tenant.domain;

/** Estados de um {@link Membership} (entities.md §6.3). */
public enum MembershipStatus {
    INVITED,
    /** INV-MEM-04: exige {@code acceptedAt} preenchido. */
    ACTIVE,
    /** CE-P-09: token válido é rejeitado com {@code 403 DEVTIME-1102}. */
    SUSPENDED,
    REMOVED
}
