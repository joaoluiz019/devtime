package com.devtime.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Visão da conta consumida por {@code 001-authentication} (spec 001 §26).
 *
 * <p>Não carrega {@code passwordHash}. A ausência é estrutural, não convenção: INV-USR-02 e CP-01
 * exigem que o hash nunca saia da feature que o guarda, e a verificação de senha é feita por {@code
 * UserAccountService.matchesPassword} — o hash não atravessa a fronteira em nenhum caminho.
 *
 * @param preferences JSON de {@code entities.md} §6.2.1, repassado tal como persistido para {@code
 *     GET /auth/me}
 */
public record UserAccount(
        UUID id,
        String email,
        String fullName,
        String displayName,
        String avatarUrl,
        AccountStatus status,
        Instant emailVerifiedAt,
        Instant lockedUntil,
        short failedLoginAttempts,
        Instant passwordChangedAt,
        String timezone,
        String locale,
        String preferences) {

    /** RN-453: o bloqueio vale enquanto {@code lockedUntil} estiver no futuro. */
    public boolean isLockedAt(Instant reference) {
        return lockedUntil != null && reference.isBefore(lockedUntil);
    }

    /** CP-08: apenas conta com e-mail verificado autentica. */
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
