package com.devtime.auth;

import com.devtime.user.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Jobs de manutenção da feature 001 (spec §22.4, T-001-36).
 *
 * <p>Reunidos em uma classe porque compartilham a mesma natureza — limpeza por predicado, sem
 * estado, sem parâmetro — e separá-los em três arquivos multiplicaria cerimônia sem separar
 * responsabilidade.
 *
 * <p>BR-184: todos usam {@code @SchedulerLock} com {@code lockAtMostFor} folgado em relação à
 * duração esperada, para que uma execução travada não seja reexecutada em paralelo por outra
 * réplica. BR-185: todos são idempotentes — operam por predicado sobre o estado atual, então
 * reexecutar não produz efeito diferente.
 *
 * <p>Ativos apenas no perfil {@code scheduler}, como o restante do agendamento (backend.md §13.1).
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class AuthCleanupJobs {

    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final UserAccountService userAccountService;

    /** RT-08: remove tokens expirados ou revogados há mais de 30 dias. */
    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "PT10M")
    public void cleanupRefreshTokens() {
        int removed = refreshTokenService.purgeSettled();
        log.info("limpeza de refresh tokens concluída removidos={}", removed);
    }

    /** Remove tokens de verificação, redefinição e convite já consumidos ou vencidos. */
    @Scheduled(cron = "0 15 4 * * *")
    @SchedulerLock(name = "verificationTokenCleanup", lockAtMostFor = "PT10M")
    public void cleanupVerificationTokens() {
        int removed = verificationTokenService.purgeSettled();
        log.info("limpeza de tokens de verificação concluída removidos={}", removed);
    }

    /**
     * §11 de spec 001: {@code LOCKED → ACTIVE} quando {@code lockedUntil} vence.
     *
     * <p>De dez em dez minutos, mas o login também desbloqueia sozinho ao encontrar o prazo vencido
     * (ver {@code LoginAttemptService}). O job existe para que a conta apareça desbloqueada em
     * consultas administrativas mesmo que o titular não tente entrar.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(name = "unlockAccounts", lockAtMostFor = "PT5M")
    public void unlockExpiredAccounts() {
        int unlocked = userAccountService.unlockExpiredAccounts();
        if (unlocked > 0) {
            log.info("contas desbloqueadas automaticamente quantidade={}", unlocked);
        }
    }
}
