package com.devtime.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Saídas de perfil (users.md §5). */
public final class UserProfileResponses {

    private UserProfileResponses() {}

    /**
     * Perfil do usuário autenticado.
     *
     * <p>INV-USR-02: {@code passwordHash} está ausente por construção — o record não possui o
     * componente, e não apenas o omite no mapeamento. {@code failedLoginAttempts}, {@code
     * lockedUntil} e {@code passwordChangedAt} também ficam de fora: são estado de segurança,
     * servidos por {@code 001} onde fazem sentido, não dados de perfil.
     *
     * @param avatarUrl URL assinada de leitura; nula quando não há avatar
     */
    @Schema(name = "UserProfileResponse")
    public record UserProfileResponse(
            UUID id,
            String email,
            String fullName,
            String displayName,
            String avatarUrl,
            String timezone,
            String locale,
            UserPreferences preferences,
            long version) {}
}
