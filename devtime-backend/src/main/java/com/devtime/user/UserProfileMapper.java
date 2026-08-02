package com.devtime.user;

import com.devtime.user.domain.User;
import com.devtime.user.dto.UserProfileResponses.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link User} para o perfil exposto (users.md §5).
 *
 * <p>Escrito à mão porque os dois campos que importam não são cópias: {@code preferences} vem de
 * {@code JSONB} com padrões aplicados e {@code avatarUrl} é uma URL assinada derivada da chave
 * armazenada. INV-USR-02 é garantido pelo formato do DTO, que não possui o componente.
 */
@Component
@RequiredArgsConstructor
public class UserProfileMapper {

    private final UserPreferencesCodec preferencesCodec;
    private final AvatarUrlResolver avatarUrlResolver;

    public UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getDisplayName(),
                avatarUrlResolver.resolve(user.getAvatarUrl()),
                user.getTimezone(),
                user.getLocale(),
                preferencesCodec.read(user.getPreferences()),
                user.getVersion() == null ? 0L : user.getVersion());
    }
}
