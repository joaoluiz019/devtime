package com.devtime.user;

import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.user.domain.User;
import com.devtime.user.domain.UserStatus;
import com.devtime.user.dto.AccountStatus;
import com.devtime.user.dto.UserAccount;
import com.devtime.user.dto.UserCommands.LoginFailureOutcome;
import com.devtime.user.dto.UserCommands.LoginLockPolicy;
import com.devtime.user.dto.UserCommands.NewAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link UserAccountService}.
 *
 * <p>Todo acesso ao {@code passwordHash} está confinado a esta classe (INV-USR-02): ele entra pelo
 * {@link PasswordEncoder} e nunca sai — nem por retorno, nem por log, nem por exceção.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountServiceImpl implements UserAccountService {

    /**
     * Hash BCrypt válido de uma senha aleatória descartada, usado apenas para consumir tempo.
     *
     * <p>AU-02 / SG-03: quando o e-mail não existe, a comparação é feita contra este valor para que
     * a resposta demore o mesmo que uma senha incorreta. Precisa ser um hash real e com o mesmo
     * custo de produção — um valor inválido faria o encoder retornar imediatamente, restaurando a
     * diferença de tempo que a defesa existe para eliminar.
     */
    private static final String TIMING_DEFENSE_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.7Wp0.4vTJ5v/RcW6sTfhpDrRW1kX4Iu";

    private static final String DEFAULT_PREFERENCES = "{}";

    /** §19.1: domínio reservado, não roteável — nenhuma mensagem chega a ele por acidente. */
    private static final String ANONYMIZED_EMAIL_DOMAIN = "@anonimizado.local";

    /** §19.1: mesmo texto que {@code UserSummary.REMOVED_USER_NAME} exibe (RN-458). */
    private static final String ANONYMIZED_NAME = "Usuário Removido";

    /**
     * Valor que nenhum BCrypt produz e que o encoder recusa em qualquer comparação.
     *
     * <p>Preferido a {@code null}: a coluna é {@code NOT NULL}, e um hash válido de senha aleatória
     * deixaria em aberto a hipótese — remota, mas real — de alguém acertá-la.
     */
    private static final String DISCARDED_PASSWORD_HASH = "!";

    private final UserRepository repository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        return repository.findByEmailIgnoringCase(normalizedEmail).map(this::toAccount);
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return userId == null ? Optional.empty() : repository.findById(userId).map(this::toAccount);
    }

    @Override
    public java.util.Map<UUID, UserAccount> findAllByIds(java.util.Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Map.of();
        }
        return repository.findAllByIdIn(userIds).stream()
                .map(this::toAccount)
                .collect(java.util.stream.Collectors.toMap(UserAccount::id, account -> account));
    }

    @Override
    public java.util.List<UUID> findIdsMatching(String term) {
        if (term == null || term.isBlank()) {
            return java.util.List.of();
        }
        return repository.findIdsMatching(term.trim().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public UserAccount require(UUID userId) {
        return findById(userId).orElseThrow(() -> EntityNotFoundException.of(User.class, userId));
    }

    @Override
    @Transactional
    public UUID create(NewAccount command) {
        User user = new User();
        user.setEmail(command.normalizedEmail());
        user.setPasswordHash(passwordEncoder.encode(command.rawPassword()));
        user.setFullName(command.fullName());
        user.setDisplayName(firstNameOf(command.fullName()));
        // CP-08: nasce pendente. O access token só é emitido após a verificação do e-mail.
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setFailedLoginAttempts((short) 0);
        user.setPasswordChangedAt(clock.instant());
        user.setTimezone(command.timezone());
        user.setPreferences(DEFAULT_PREFERENCES);
        // saveAndFlush força a violação de uq_users_email a aparecer aqui, e não no commit: no
        // commit, a transação já estaria fora do alcance do tratamento e CX-02 viraria 500.
        return repository.saveAndFlush(user).getId();
    }

    @Override
    public boolean matchesPassword(UUID userId, String rawPassword) {
        return repository
                .findById(userId)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .orElse(false);
    }

    @Override
    public void burnPasswordComparison() {
        passwordEncoder.matches("timing-defense", TIMING_DEFENSE_HASH); // AU-02
    }

    @Override
    @Transactional
    public LoginFailureOutcome registerLoginFailure(UUID userId, LoginLockPolicy policy) {
        User user = require(userId, User.class);
        Instant now = clock.instant();

        // RN-453: a janela de 15 minutos reinicia quando a falha anterior é antiga demais. Sem o
        // reinício, erros de digitação esparsos ao longo de meses somariam até o bloqueio.
        boolean windowExpired =
                user.getLastFailedLoginAt() == null
                        || user.getLastFailedLoginAt().plus(policy.window()).isBefore(now);
        int attempts = windowExpired ? 1 : user.getFailedLoginAttempts() + 1;

        user.setFailedLoginAttempts((short) Math.min(attempts, Short.MAX_VALUE));
        user.setLastFailedLoginAt(now);

        boolean alreadyLocked =
                user.getLockedUntil() != null && now.isBefore(user.getLockedUntil());
        if (attempts >= policy.maxAttempts() && !alreadyLocked) {
            Instant lockedUntil = now.plus(policy.lockDuration());
            user.setLockedUntil(lockedUntil); // INV-USR-03: sempre em conjunto com o status
            user.setStatus(UserStatus.LOCKED);
            return new LoginFailureOutcome(attempts, lockedUntil);
        }
        // AC-001-43: falhas concorrentes na 5ª tentativa não reemitem o alerta de segurança.
        return new LoginFailureOutcome(attempts, null);
    }

    @Override
    @Transactional
    public void registerLoginSuccess(UUID userId) {
        User user = require(userId, User.class);
        user.setFailedLoginAttempts((short) 0); // RN-453
        user.setLastFailedLoginAt(null);
        user.setLockedUntil(null);
        user.setLastLoginAt(clock.instant());
    }

    @Override
    @Transactional
    public boolean unlockIfExpired(UUID userId) {
        User user = require(userId, User.class);
        Instant now = clock.instant();
        if (user.getLockedUntil() == null || now.isBefore(user.getLockedUntil())) {
            return false;
        }
        applyUnlock(user);
        return true;
    }

    @Override
    @Transactional
    public void markEmailVerified(UUID userId) {
        User user = require(userId, User.class);
        if (user.getEmailVerifiedAt() != null) {
            // CE-AU-04: a verificação é idempotente; reexecutar não altera emailVerifiedAt.
            return;
        }
        Instant now = clock.instant();
        user.setEmailVerifiedAt(now);
        // §11 de spec 001: apenas PENDING_ACTIVATION progride. DISABLED e LOCKED não são
        // reativados por um link de e-mail — seriam transições proibidas.
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
    }

    @Override
    @Transactional
    public void completeProfile(UUID userId, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return;
        }
        User user = require(userId, User.class);
        // Só preenche o que ainda não existe: o aceite de convite não é rota de renomeação, e
        // permitir a sobrescrita deixaria o nome do titular à mercê de quem tem o link.
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(fullName);
        }
        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            user.setDisplayName(firstNameOf(user.getFullName()));
        }
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String newRawPassword) {
        User user = require(userId, User.class);
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        user.setPasswordChangedAt(clock.instant()); // TK-04
        // CX-07: redefinir a senha desbloqueia a conta. Quem prova a posse do e-mail já demonstrou
        // que não é o atacante cujas tentativas causaram o bloqueio.
        applyUnlock(user);
    }

    @Override
    @Transactional
    public int unlockExpiredAccounts() {
        Instant now = clock.instant();
        var expired = repository.findLockExpired(now);
        expired.forEach(this::applyUnlock);
        return expired.size();
    }

    /** RN-008 / §19.1 (ver {@link UserAccountService#anonymize}). */
    @Override
    @Transactional
    public int anonymize(java.util.Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        int anonymized = 0;
        for (User user : repository.findAllByIdIn(userIds)) {
            if (user.getEmail().endsWith(ANONYMIZED_EMAIL_DOMAIN)) {
                continue; // Convergente: reexecutar a purga não conta a mesma conta duas vezes.
            }
            // O hash vem do identificador, não do e-mail: derivá-lo do e-mail permitiria confirmar
            // um endereço por comparação, que é justamente o dado que a anonimização remove.
            user.setEmail(
                    "usuario-"
                            + Integer.toHexString(user.getId().hashCode())
                            + ANONYMIZED_EMAIL_DOMAIN);
            user.setFullName(ANONYMIZED_NAME);
            user.setDisplayName(null);
            user.setAvatarUrl(null);
            user.setPreferences(null);
            user.setTimezone(null);
            user.setLocale(null);
            // §19.1: descartado, não anonimizado — a conta não deve autenticar por caminho algum.
            user.setPasswordHash(DISCARDED_PASSWORD_HASH);
            user.setStatus(UserStatus.DISABLED);
            anonymized++;
        }
        return anonymized;
    }

    private void applyUnlock(User user) {
        user.setLockedUntil(null);
        user.setFailedLoginAttempts((short) 0);
        user.setLastFailedLoginAt(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
    }

    private User require(UUID userId, Class<User> type) {
        return repository
                .findById(userId)
                .orElseThrow(() -> EntityNotFoundException.of(type, userId));
    }

    /**
     * Atualização parcial das preferências de notificação (ver {@link
     * UserAccountService#updateNotificationPreferences}).
     *
     * <p>Sem {@code @PreAuthorize}: quem verifica a permissão é {@code 013-notifications}, no
     * endpoint que só alcança as preferências do <b>próprio</b> usuário autenticado.
     *
     * <p>O JSON é mesclado, não substituído: escrever o objeto inteiro apagaria {@code theme},
     * {@code dashboardPeriod} e {@code defaultCategoryId} a cada troca de preferência de e-mail.
     */
    @Override
    @Transactional
    public void updateNotificationPreferences(
            UUID userId,
            Boolean emailNotifications,
            java.util.List<String> mutedNotificationTypes) {
        User user =
                repository
                        .findById(userId)
                        .orElseThrow(() -> EntityNotFoundException.of(User.class, userId));

        java.util.Map<String, Object> preferences = readPreferences(user.getPreferences());
        if (emailNotifications != null) {
            preferences.put("emailNotifications", emailNotifications);
        }
        if (mutedNotificationTypes != null) {
            preferences.put(
                    "mutedNotificationTypes", java.util.List.copyOf(mutedNotificationTypes));
        }
        user.setPreferences(writePreferences(preferences));
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> readPreferences(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return new java.util.LinkedHashMap<>(objectMapper.readValue(json, java.util.Map.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            // Degradar para um objeto vazio é preferível a impedir o usuário de ajustar as
            // preferências de um JSON que ele não tem como corrigir (ER-08).
            return new java.util.LinkedHashMap<>();
        }
    }

    private String writePreferences(java.util.Map<String, Object> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Falha ao serializar preferências", failure);
        }
    }

    private UserAccount toAccount(User user) {
        return new UserAccount(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                AccountStatus.valueOf(user.getStatus().name()),
                user.getEmailVerifiedAt(),
                user.getLockedUntil(),
                user.getFailedLoginAttempts(),
                user.getPasswordChangedAt(),
                user.getTimezone(),
                user.getLocale(),
                user.getPreferences());
    }

    /** entities.md §6.2: {@code displayName} tem como padrão o primeiro nome. */
    private String firstNameOf(String fullName) {
        String trimmed = fullName == null ? "" : fullName.strip();
        int space = trimmed.indexOf(' ');
        String first = space < 0 ? trimmed : trimmed.substring(0, space);
        return first.length() > 60 ? first.substring(0, 60) : first;
    }
}
