package com.devtime.worklog;

import com.devtime.shared.time.TenantClock;
import com.devtime.worklog.domain.WorkLogExceptions;
import com.devtime.worklog.domain.WorkLogInterval;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validações temporais e de conteúdo do registro de horas (spec 008 §22.3).
 *
 * <p>BR-068: validação complexa isolada do serviço. Cada método corresponde a uma regra e nomeia-a
 * no comentário, de modo que a ordem normativa da §6.1 fique legível no serviço como uma sequência
 * de chamadas — que é o que o teste de ordem (CA-09) verifica.
 */
@Component
@RequiredArgsConstructor
public class WorkLogValidator {

    /** RN-103 / INV-WKL-03. */
    public static final int MAX_GROSS_MINUTES = 1440;

    /** RS-09 / RN-118: tolerância para diferença de relógio entre cliente e servidor. */
    public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(2);

    /** RN-105 / RN-158. */
    public static final int MIN_DESCRIPTION_LENGTH = 3;

    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final TenantClock clock;

    /** RN-114 / INV-WKL-01: não existe trabalho de duração nula ou negativa. */
    public void assertChronological(WorkLogInterval interval) {
        if (!interval.startedAt().isBefore(interval.endedAt())) {
            throw WorkLogExceptions.rangeInvalid();
        }
    }

    /**
     * RN-103: o limite é aplicado ao tempo <b>bruto</b>, não ao líquido.
     *
     * <p>Uma sessão maior que 24h é sempre erro de digitação ou cronômetro esquecido; verificar o
     * líquido permitiria uma sessão de 30 horas com 8 de pausa, que continua sendo o mesmo erro.
     */
    public void assertWithinMaxDuration(int grossMinutes) {
        if (grossMinutes > MAX_GROSS_MINUTES) {
            throw WorkLogExceptions.sessionTooLong(grossMinutes);
        }
    }

    /**
     * RN-118: o término não pode estar no futuro, com tolerância de 2 minutos.
     *
     * <p>A tolerância existe porque o horário chega do relógio do cliente, que pode estar alguns
     * segundos adiantado. Sem ela, encerrar um cronômetro em uma máquina com relógio adiantado
     * falharia sem que o usuário tivesse como entender o motivo (CX-09, CX-10).
     */
    public void assertNotInFuture(WorkLogInterval interval) {
        var limit = clock.now().plus(FUTURE_TOLERANCE);
        if (interval.endedAt().isAfter(limit)) {
            throw WorkLogExceptions.endedInFuture(interval.endedAt(), limit);
        }
    }

    /** RN-119: data futura só com {@code tenant.settings.allowFutureWorkLogs}. */
    public void assertFutureDateAllowed(LocalDate workDate, boolean allowFutureWorkLogs) {
        LocalDate today = clock.today();
        if (!allowFutureWorkLogs && workDate.isAfter(today)) {
            throw WorkLogExceptions.futureDateNotAllowed(workDate, today);
        }
    }

    /** RN-116 / INV-WKL-04: {@code 0 ≤ pausedMinutes < grossMinutes}. */
    public void assertPausedMinutesCoherent(int pausedMinutes, int grossMinutes) {
        if (pausedMinutes < 0 || pausedMinutes >= grossMinutes) {
            throw WorkLogExceptions.pausedMinutesInvalid(pausedMinutes, grossMinutes);
        }
    }

    /**
     * RN-115 / INV-WKL-02: registro vazio polui o relatório sem informar nada.
     *
     * @param netMinutesBeforeRounding valor antes de RN-113, devolvido nos detalhes para que o
     *     usuário entenda a rejeição quando é o arredondamento que zera o líquido (OB-05)
     */
    public void assertPositiveNetMinutes(int netMinutes, int netMinutesBeforeRounding) {
        if (netMinutes <= 0) {
            throw WorkLogExceptions.netMinutesInvalid(netMinutes, netMinutesBeforeRounding);
        }
    }

    /**
     * RN-105: 3 a 2.000 caracteres <b>após aparar as bordas</b> (CX-16).
     *
     * <p>A descrição é o que o cliente lê no relatório; uma descrição só com espaços produziria uma
     * linha em branco em um documento que sustenta uma cobrança.
     *
     * @return a descrição já aparada, que é a forma persistida
     */
    public String requireDescription(String rawDescription) {
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.length() < MIN_DESCRIPTION_LENGTH
                || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw WorkLogExceptions.descriptionInvalid(description.length());
        }
        return description;
    }
}
