package com.devtime.auth;

import com.devtime.auth.domain.AuthExceptions;
import com.devtime.auth.event.AuthEvents.AccountLockedEvent;
import com.devtime.shared.event.DomainEventPublisher;
import com.devtime.user.UserAccountService;
import com.devtime.user.dto.UserAccount;
import com.devtime.user.dto.UserCommands.LoginFailureOutcome;
import com.devtime.user.dto.UserCommands.LoginLockPolicy;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link LoginAttemptService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** RN-453: 5 falhas em 15 minutos bloqueiam por 30 minutos. */
    static final LoginLockPolicy POLICY =
            new LoginLockPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(30));

    private final UserAccountService userAccountService;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public UserAccount assertNotLocked(UserAccount account) {
        if (account.isLockedAt(clock.instant())) {
            throw AuthExceptions.accountLocked(account.lockedUntil()); // RN-453
        }
        // §11 de spec 001: LOCKED → ACTIVE assim que lockedUntil vence, sem aguardar o job de dez
        // em dez minutos. Sem isto, a conta continuaria recusando login após o prazo cumprido.
        if (!userAccountService.unlockIfExpired(account.id())) {
            return account;
        }
        return userAccountService.findById(account.id()).orElse(account);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code REQUIRES_NEW} justificado por CE-B-02 / BR-122: <b>registro de falha</b>. O login
     * termina em {@code 401}, e uma exceção desfaz a transação que a originou. Se o incremento
     * participasse dela, o contador voltaria a zero a cada tentativa e RN-453 jamais bloquearia
     * conta alguma — o contador existe precisamente para sobreviver ao erro que o produziu.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(UserAccount account) {
        LoginFailureOutcome outcome = userAccountService.registerLoginFailure(account.id(), POLICY);
        if (!outcome.justLocked()) {
            return;
        }
        // §28: WARN com userId e lockedUntil. Nunca o e-mail em claro nem a senha tentada.
        log.warn(
                "conta bloqueada por tentativas de acesso userId={} lockedUntil={}",
                account.id(),
                outcome.lockedUntil());
        // AU-06: o alerta sai após o commit; o bloqueio não depende do e-mail ter sido entregue.
        events.publish(
                new AccountLockedEvent(
                        account.id(), account.email(), account.fullName(), outcome.lockedUntil()));
    }

    @Override
    @Transactional
    public void registerSuccess(UserAccount account) {
        userAccountService.registerLoginSuccess(account.id());
    }
}
