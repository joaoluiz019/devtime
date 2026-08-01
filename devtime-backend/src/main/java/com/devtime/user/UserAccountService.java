package com.devtime.user;

import com.devtime.user.dto.UserAccount;
import com.devtime.user.dto.UserCommands.LoginFailureOutcome;
import com.devtime.user.dto.UserCommands.LoginLockPolicy;
import com.devtime.user.dto.UserCommands.NewAccount;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida da credencial, consumido por {@code 001-authentication}.
 *
 * <p>Separado de {@link UserService} de propósito: aquele serve à <b>exibição</b> de pessoas
 * (menções, responsáveis) e é consumido por várias features; este serve à <b>autenticação</b> e tem
 * um único consumidor. Reuni-los exporia operações de credencial a {@code ticket} e {@code
 * comment}, que não têm razão para alcançá-las.
 *
 * <p>Nenhum método devolve o hash da senha (INV-USR-02). A comparação acontece aqui dentro.
 */
public interface UserAccountService {

    /**
     * RN-452 / AU-03: busca pelo e-mail já normalizado.
     *
     * @param normalizedEmail resultado de {@code EmailNormalizer.normalize}
     */
    Optional<UserAccount> findByEmail(String normalizedEmail);

    Optional<UserAccount> findById(UUID userId);

    /**
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} quando a conta
     *     não existe
     */
    UserAccount require(UUID userId);

    /**
     * Cria a conta em {@code PENDING_ACTIVATION} com hash BCrypt custo 12 (ART-081).
     *
     * @return identificador da conta criada
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2452} quando o e-mail
     *     já existe — inclusive quando a violação só é detectada pelo índice único, sob
     *     concorrência (CX-02, AC-001-40)
     */
    UUID create(NewAccount command);

    /**
     * @return {@code true} quando a senha confere com o hash persistido
     */
    boolean matchesPassword(UUID userId, String rawPassword);

    /**
     * SG-03 / AU-02: executa uma comparação BCrypt descartável.
     *
     * <p>Chamado quando o e-mail não corresponde a nenhuma conta, para que o tempo de resposta seja
     * indistinguível do de uma senha incorreta. Sem isso, a diferença de latência transformaria o
     * login em oráculo de existência de e-mail.
     */
    void burnPasswordComparison();

    /** RN-453: incrementa o contador e bloqueia ao atingir o limite dentro da janela. */
    LoginFailureOutcome registerLoginFailure(UUID userId, LoginLockPolicy policy);

    /** RN-453: zera o contador e atualiza {@code lastLoginAt}. */
    void registerLoginSuccess(UUID userId);

    /**
     * §11 de spec 001: {@code LOCKED → ACTIVE} quando {@code lockedUntil} já passou.
     *
     * @return {@code true} se a conta foi desbloqueada nesta chamada
     */
    boolean unlockIfExpired(UUID userId);

    /** §4.2 de state-machines.md: conclui a verificação de e-mail. */
    void markEmailVerified(UUID userId);

    /**
     * Define o nome do titular no aceite de convite (§5.12).
     *
     * <p>Necessário porque quem convida informa apenas o e-mail: o nome só existe quando a pessoa
     * convidada conclui o próprio cadastro. Ignorado quando a conta já possui nome — o aceite não é
     * caminho para renomear alguém.
     */
    void completeProfile(UUID userId, String fullName);

    /**
     * Troca a senha, atualiza {@code passwordChangedAt} (TK-04) e desfaz bloqueio (CX-07).
     *
     * <p>A revogação de sessões (RN-454) <b>não</b> ocorre aqui: refresh tokens pertencem a {@code
     * auth}, e o orquestrador é quem decide se a sessão corrente sobrevive (troca de senha) ou não
     * (redefinição).
     */
    void changePassword(UUID userId, String newRawPassword);

    /** {@code UnlockExpiredAccountsJob}: desbloqueio em lote (T-001-36). */
    int unlockExpiredAccounts();

    /**
     * Substitui as chaves de notificação em {@code user.preferences} (entities.md §6.2.1).
     *
     * <p>Interface pública para {@code 013-notifications}, que serve {@code PATCH
     * /notifications/preferences}. As demais chaves de {@code preferences} — tema, painel,
     * categoria padrão — <b>não</b> são tocadas: uma alteração de preferência de notificação não
     * pode apagar o tema escolhido pelo usuário.
     *
     * <p>Fica aqui, e não em {@code 013}, porque {@code preferences} é coluna de {@code users} e
     * AR-02 impede que outra feature a alcance. A auditoria da alteração pertence a {@code
     * 002-users} (§18 da spec 013).
     *
     * @param emailNotifications nulo mantém o valor atual — atualização parcial (§9.2)
     * @param mutedNotificationTypes nulo mantém o valor atual
     */
    void updateNotificationPreferences(
            UUID userId, Boolean emailNotifications, java.util.List<String> mutedNotificationTypes);
}
