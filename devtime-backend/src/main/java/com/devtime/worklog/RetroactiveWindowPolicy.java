package com.devtime.worklog;

import com.devtime.shared.security.Role;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.worklog.domain.WorkLogExceptions;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Janela de lançamento retroativo (RN-120).
 *
 * <p>BR-067: estratégia configurável por {@code tenant.settings.retroactiveLimitDays} (padrão 30).
 * O objetivo é evitar a reescrita de meses antigos por engano — um work log lançado com data de
 * março altera um saldo que o cliente já conferiu —, mantendo flexibilidade sob responsabilidade:
 * {@code ADMIN} e {@code OWNER} lançam fora da janela, porque são quem responde comercialmente pelo
 * tenant.
 *
 * <p>A exceção é por <b>papel</b>, e não por permissão dedicada: {@code permissions.md} §7 não
 * define uma permissão para isso, e inventar uma seria criar regra não documentada (IA-01). RN-120
 * nomeia os dois papéis diretamente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetroactiveWindowPolicy {

    /** RN-120: os únicos papéis autorizados a lançar fora da janela. */
    private static final Set<Role> ROLES_BEYOND_WINDOW = Set.of(Role.ADMIN, Role.OWNER);

    private final TenantContext tenantContext;
    private final TenantClock clock;

    /**
     * @param retroactiveLimitDays dias de tolerância antes de hoje; vindo de {@code
     *     tenant.settings}
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2120} / {@code 422}
     */
    public void assertWithinWindow(LocalDate workDate, int retroactiveLimitDays) {
        LocalDate earliestAllowed = clock.today().minusDays(retroactiveLimitDays);
        if (!workDate.isBefore(earliestAllowed)) {
            return;
        }
        boolean allowed =
                tenantContext.currentRole().map(ROLES_BEYOND_WINDOW::contains).orElse(false);
        if (!allowed) {
            throw WorkLogExceptions.retroactiveLimit(workDate, retroactiveLimitDays);
        }
        log.info(
                "lançamento retroativo além da janela workDate={} retroactiveLimitDays={}",
                workDate,
                retroactiveLimitDays);
    }
}
