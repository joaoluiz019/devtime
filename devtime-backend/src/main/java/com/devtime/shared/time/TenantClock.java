package com.devtime.shared.time;

import com.devtime.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Agora" no fuso do tenant (backend.md §5, BR-142).
 *
 * <p>BR-140/BR-141 proíbem {@code Instant.now()} e {@code LocalDate.now()} no código: o instante
 * vem sempre do {@link Clock} injetado, o que torna o comportamento determinístico em teste
 * (BR-205).
 *
 * <p>A distinção entre {@link #now()} e {@link #today()} é a mesma de ART-030 e ART-031: instante é
 * sempre UTC; data de calendário é sempre a data <b>no fuso do tenant</b>. Confundir as duas produz
 * registros de horas atribuídos ao dia errado próximo à meia-noite (RN-108).
 */
@Component
@RequiredArgsConstructor
public class TenantClock {

    private final Clock clock;
    private final TenantContext tenantContext;

    /** Instante atual em UTC (ART-030). */
    public Instant now() {
        return clock.instant();
    }

    /** Data de calendário atual no fuso do tenant (ART-031). */
    public LocalDate today() {
        return toTenantDate(now());
    }

    public LocalDate toTenantDate(Instant instant) {
        return instant.atZone(tenantZone()).toLocalDate();
    }

    /**
     * Fuso do tenant da sessão.
     *
     * <p>Recorre a UTC quando não há fuso na sessão — por exemplo em job de plataforma antes de
     * definir o tenant da iteração. UTC é escolhido em vez do fuso padrão da JVM porque o fuso da
     * máquina é um detalhe de infraestrutura: fazer a data de negócio depender dele tornaria o
     * resultado diferente entre ambientes.
     */
    public ZoneId tenantZone() {
        return tenantContext.currentTimezone().map(ZoneId::of).orElse(ZoneOffset.UTC);
    }
}
