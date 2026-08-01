package com.devtime.notification;

import com.devtime.notification.domain.NotificationType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Montagem da chave de deduplicação (RN-601, RN-603, §6.1 da spec).
 *
 * <p>A chave identifica o <b>evento lógico</b>, não a ocorrência: {@code
 * CONTRACT_USAGE:{periodId}:80} é a mesma chave na primeira e na vigésima avaliação do limiar no
 * mesmo dia. É isso que garante <b>um alerta por limiar por período, para sempre</b> — e o que
 * impede a oscilação de consumo de virar ruído (§6.3).
 *
 * <p>Existe como classe própria, e não como concatenação espalhada pelos consumidores de evento,
 * porque uma divergência de formato entre dois produtores criaria duas chaves para o mesmo evento —
 * e a deduplicação deixaria de funcionar sem que nada falhasse visivelmente.
 *
 * <p>O formato é {@code {tipo}:{entidade}:{discriminador}}, com o discriminador presente apenas
 * onde a §6.1 o define.
 */
@Component
public class DedupeKeyBuilder {

    private static final String SEPARATOR = ":";

    /**
     * RN-603: {@code CONTRACT_USAGE:{periodId}:{threshold}}.
     *
     * <p>O limiar entra na chave, e não o tipo derivado: um contrato com {@code [70, 90]} produz
     * {@code :70} e {@code :90}, e não as chaves fixas de 50/80/100 (CP-05).
     */
    public String consumption(UUID contractPeriodId, int threshold) {
        return join("CONTRACT_USAGE", contractPeriodId, String.valueOf(threshold));
    }

    /** RN-604: {@code CONTRACT_OVERAGE:{periodId}} — uma única vez por período. */
    public String overage(UUID contractPeriodId) {
        return join("CONTRACT_OVERAGE", contractPeriodId, null);
    }

    /** RN-605. */
    public String periodClosing(UUID contractPeriodId) {
        return join("PERIOD_CLOSING", contractPeriodId, null);
    }

    /**
     * CX-08: {@code PERIOD_CLOSED:{periodId}} <b>sem</b> discriminador.
     *
     * <p>Um período reaberto e refechado não gera nova notificação de fechamento — o fato "este
     * período foi fechado" já foi comunicado. O que muda com a reabertura tem tipo próprio.
     */
    public String periodClosed(UUID contractPeriodId) {
        return join("PERIOD_CLOSED", contractPeriodId, null);
    }

    /**
     * §6 de notifications.md: {@code PERIOD_REOPENED:{periodId}:{reopenCount}}.
     *
     * <p>É a única chave de período com discriminador, e por um motivo específico (CE-N-13): cada
     * reabertura é um fato novo — um relatório entregue foi alterado outra vez —, enquanto o
     * fechamento é sempre o mesmo fato.
     */
    public String periodReopened(UUID contractPeriodId, int reopenCount) {
        return join("PERIOD_REOPENED", contractPeriodId, String.valueOf(reopenCount));
    }

    /** RN-606: {@code CONTRACT_ENDING:{contractId}}. */
    public String contractEnding(UUID contractId) {
        return join("CONTRACT_ENDING", contractId, null);
    }

    /** RN-215: {@code ADJUSTMENT:{adjustmentId}:{userId}} — um por destinatário. */
    public String adjustmentApplied(UUID adjustmentId, UUID recipientId) {
        return join("ADJUSTMENT", adjustmentId, recipientId.toString());
    }

    /** RN-163: {@code TIMER_LONG:{timerId}}. */
    public String timerLongRunning(UUID timerId) {
        return join("TIMER_LONG", timerId, null);
    }

    /** RN-164: {@code TIMER_ABANDONED:{timerId}}. */
    public String timerAbandoned(UUID timerId) {
        return join("TIMER_ABANDONED", timerId, null);
    }

    /** §8.7 de worklogs.md: {@code TIMER_FORCED:{timerId}}. */
    public String timerForceStopped(UUID timerId) {
        return join("TIMER_FORCED", timerId, null);
    }

    /**
     * RN-607: {@code TICKET_ASSIGNED:{ticketId}:{assigneeId}}.
     *
     * <p>CX-22: o responsável entra na chave para que uma reatribuição de volta a alguém que já foi
     * responsável do mesmo ticket não seja silenciada — para essa pessoa, é a segunda vez que o
     * ticket chega, mas a chave é a mesma. É a consequência aceita de RN-601 aplicada a este tipo.
     */
    public String ticketAssigned(UUID ticketId, UUID assigneeId) {
        return join("TICKET_ASSIGNED", ticketId, assigneeId.toString());
    }

    /** RN-312: {@code TICKET_REOPENED:{ticketId}:{workLogId}} — cada reabertura é um fato novo. */
    public String ticketReopened(UUID ticketId, UUID workLogId) {
        return join("TICKET_REOPENED", ticketId, workLogId.toString());
    }

    /** RN-813: {@code TICKET_COMMENT:{commentId}:{userId}}. */
    public String ticketCommented(UUID commentId, UUID recipientId) {
        return join("TICKET_COMMENT", commentId, recipientId.toString());
    }

    /** RN-813: {@code TICKET_MENTION:{commentId}:{userId}}. */
    public String ticketMentioned(UUID commentId, UUID recipientId) {
        return join("TICKET_MENTION", commentId, recipientId.toString());
    }

    /**
     * Chave genérica para os tipos ainda sem produtor.
     *
     * <p>Mantém o formato de §6.1 para quando {@code 002}, {@code 012} e {@code 015} chegarem, em
     * vez de deixar cada uma inventar o seu.
     */
    public String forType(NotificationType type, UUID entityId, String discriminator) {
        return join(type.name(), entityId, discriminator);
    }

    private String join(String prefix, UUID entityId, String discriminator) {
        String key = prefix + SEPARATOR + entityId;
        return discriminator == null ? key : key + SEPARATOR + discriminator;
    }
}
