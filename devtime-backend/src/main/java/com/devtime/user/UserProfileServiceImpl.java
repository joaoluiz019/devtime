package com.devtime.user;

import com.devtime.audit.AuditService;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.persistence.UuidGenerator;
import com.devtime.shared.storage.StoragePort;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TimezoneValidator;
import com.devtime.user.domain.User;
import com.devtime.user.dto.UserProfileRequests.UserPreferencesRequest;
import com.devtime.user.dto.UserProfileRequests.UserProfileUpdateRequest;
import com.devtime.user.dto.UserProfileResponses.UserProfileResponse;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil, preferências e avatar (spec 002 §22.2, users.md §5).
 *
 * <p>Não declara {@code @PreAuthorize}: §16 exige apenas requisição autenticada, e o ownership é
 * estrutural — todas as operações agem sobre {@code TenantContext.requireUserId()}. Uma anotação de
 * permissão aqui sugeriria que existe um caminho para editar o perfil alheio.
 *
 * <p>§18: perfil e preferências são entidades auditadas obrigatoriamente ({@code entities.md}
 * §6.20), e a trilha registra <b>apenas os campos alterados</b> — enviar o objeto inteiro faria
 * toda troca de tema aparecer como alteração de nome, fuso e idioma.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    static final String ENTITY_TYPE = "USER";
    static final String ACTION_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    static final String ACTION_PREFERENCES_UPDATED = "USER_PREFERENCES_UPDATED";

    private final UserRepository repository;
    private final UserProfileMapper mapper;
    private final UserPreferencesCodec preferencesCodec;
    private final AvatarValidator avatarValidator;
    private final AvatarUrlResolver avatarUrlResolver;
    private final StoragePort storagePort;
    private final TimezoneValidator timezoneValidator;
    private final AuditService auditService;
    private final TenantContext tenantContext;

    @Override
    public UserProfileResponse current() {
        return mapper.toResponse(requireCurrentUser());
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request) {
        User user = requireCurrentUser();
        timezoneValidator.validate(request.timezone()); // INV-TEN-03

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        applyIfPresent(
                request.fullName(),
                "fullName",
                user.getFullName(),
                user::setFullName,
                before,
                after);
        applyIfPresent(
                request.displayName(),
                "displayName",
                user.getDisplayName(),
                user::setDisplayName,
                before,
                after);
        applyIfPresent(
                request.timezone(),
                "timezone",
                user.getTimezone(),
                user::setTimezone,
                before,
                after);
        applyIfPresent(
                request.locale(), "locale", user.getLocale(), user::setLocale, before, after);

        if (!after.isEmpty()) {
            auditService.record(ACTION_PROFILE_UPDATED, ENTITY_TYPE, user.getId(), before, after);
        }
        return mapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updatePreferences(UserPreferencesRequest request) {
        User user = requireCurrentUser();
        Map<String, Object> changes = collectPreferenceChanges(request);
        if (changes.isEmpty()) {
            return mapper.toResponse(user);
        }
        Map<String, Object> before = previousValues(user, changes.keySet());
        user.setPreferences(preferencesCodec.merge(user.getPreferences(), changes));
        auditService.record(ACTION_PREFERENCES_UPDATED, ENTITY_TYPE, user.getId(), before, changes);
        return mapper.toResponse(user);
    }

    /**
     * O binário é gravado dentro da transação, como em {@code 015-attachments}.
     *
     * <p>A ordem é a mesma de §6.1 daquela spec: tamanho antes de qualquer leitura de conteúdo,
     * depois allowlist e assinatura, e só então a gravação (CP-04). Um binário órfão no storage —
     * consequência de a transação falhar após a gravação — é reconciliável; um avatar sem binário,
     * não.
     */
    @Override
    @Transactional
    public UserProfileResponse uploadAvatar(
            long sizeBytes, String contentType, Supplier<InputStream> contentSupplier) {
        User user = requireCurrentUser();
        try (InputStream header = contentSupplier.get()) {
            avatarValidator.validate(sizeBytes, contentType, header);
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("Falha ao ler o conteúdo do avatar", unreadable);
        }

        String previousKey = user.getAvatarUrl();
        String key = AvatarUrlResolver.KEY_PREFIX + user.getId() + "/" + UuidGenerator.newId();
        try (InputStream content = contentSupplier.get()) {
            storagePort.store(key, content, sizeBytes, contentType);
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("Falha ao gravar o avatar", unreadable);
        }
        user.setAvatarUrl(key);

        // O binário anterior é removido depois de o novo existir: a ordem inversa deixaria o
        // usuário sem imagem alguma se a gravação falhasse.
        deletePreviousBinary(previousKey);
        auditService.record(
                ACTION_PROFILE_UPDATED,
                ENTITY_TYPE,
                user.getId(),
                Map.of("avatar", previousKey == null ? "none" : "present"),
                Map.of("avatar", "present"));
        return mapper.toResponse(user);
    }

    @Override
    @Transactional
    public void removeAvatar() {
        User user = requireCurrentUser();
        String key = user.getAvatarUrl();
        if (key == null) {
            return; // Idempotente: remover o que não existe não é erro.
        }
        user.setAvatarUrl(null);
        deletePreviousBinary(key);
        auditService.record(
                ACTION_PROFILE_UPDATED,
                ENTITY_TYPE,
                user.getId(),
                Map.of("avatar", "present"),
                Map.of("avatar", "none"));
    }

    private void deletePreviousBinary(String previousKey) {
        if (avatarUrlResolver.isManagedKey(previousKey)) {
            storagePort.delete(previousKey); // Idempotente por contrato da porta.
        }
    }

    private Map<String, Object> collectPreferenceChanges(UserPreferencesRequest request) {
        Map<String, Object> changes = new LinkedHashMap<>();
        putIfPresent(changes, UserPreferencesCodec.KEY_THEME, request.theme());
        putIfPresent(
                changes,
                UserPreferencesCodec.KEY_DEFAULT_CATEGORY,
                request.defaultCategoryId() == null
                        ? null
                        : request.defaultCategoryId().toString());
        putIfPresent(changes, UserPreferencesCodec.KEY_DASHBOARD_PERIOD, request.dashboardPeriod());
        putIfPresent(
                changes,
                UserPreferencesCodec.KEY_EMAIL_NOTIFICATIONS,
                request.emailNotifications());
        putIfPresent(
                changes,
                UserPreferencesCodec.KEY_MUTED_TYPES,
                request.mutedNotificationTypes() == null
                        ? null
                        : List.copyOf(request.mutedNotificationTypes()));
        putIfPresent(
                changes, UserPreferencesCodec.KEY_TIMER_REMINDER, request.timerReminderEnabled());
        return changes;
    }

    private Map<String, Object> previousValues(User user, java.util.Set<String> keys) {
        var current = preferencesCodec.read(user.getPreferences());
        Map<String, Object> previous = new LinkedHashMap<>();
        for (String key : keys) {
            previous.put(
                    key,
                    switch (key) {
                        case UserPreferencesCodec.KEY_THEME -> current.theme();
                        case UserPreferencesCodec.KEY_DEFAULT_CATEGORY ->
                                current.defaultCategoryId();
                        case UserPreferencesCodec.KEY_DASHBOARD_PERIOD -> current.dashboardPeriod();
                        case UserPreferencesCodec.KEY_EMAIL_NOTIFICATIONS ->
                                current.emailNotifications();
                        case UserPreferencesCodec.KEY_MUTED_TYPES ->
                                current.mutedNotificationTypes();
                        case UserPreferencesCodec.KEY_TIMER_REMINDER ->
                                current.timerReminderEnabled();
                        default -> null;
                    });
        }
        return previous;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /** Registra em {@code before}/{@code after} apenas quando o valor de fato muda (§18). */
    private void applyIfPresent(
            String candidate,
            String field,
            String currentValue,
            java.util.function.Consumer<String> setter,
            Map<String, Object> before,
            Map<String, Object> after) {
        if (candidate == null || candidate.equals(currentValue)) {
            return;
        }
        before.put(field, currentValue);
        after.put(field, candidate);
        setter.accept(candidate);
    }

    private User requireCurrentUser() {
        UUID userId = tenantContext.requireUserId(); // BR-041/BR-042
        return repository
                .findById(userId)
                .orElseThrow(() -> EntityNotFoundException.of(User.class, userId));
    }
}
