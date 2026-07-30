package com.devtime.audit.domain;

/** Natureza do ator de um {@link AuditLog} (entities.md §6.20). */
public enum ActorType {
    USER,
    /** CE-S-06: job de sistema. Ignora RBAC, mas respeita o escopo de tenant. */
    SYSTEM,
    /** Reservado para F8 (chaves de API). */
    API_KEY
}
