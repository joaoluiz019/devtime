package com.devtime.user.domain;

/** Estados de um {@link User} (glossário §9, state-machines.md §4.2). */
public enum UserStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    DISABLED,
    /** INV-USR-03: exige {@code lockedUntil} preenchido. */
    LOCKED
}
