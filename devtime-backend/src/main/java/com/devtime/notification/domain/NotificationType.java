package com.devtime.notification.domain;

import static com.devtime.notification.domain.NotificationSeverity.CRITICAL;
import static com.devtime.notification.domain.NotificationSeverity.INFO;
import static com.devtime.notification.domain.NotificationSeverity.WARNING;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/**
 * Catálogo de notificações (§6 de notifications.md, §12 de business-rules.md).
 *
 * <p>Cada valor carrega a severidade, o rótulo exibido na tela de preferências e se pode ser
 * silenciado. Manter os três juntos ao tipo é o que impede a divergência que §14 de
 * notifications.md prevê: "adicionar um tipo exige entrada no catálogo, chave de deduplicação,
 * destinatários, template, rótulo e teste".
 *
 * <p><b>{@code canMute = false} acompanha {@code CRITICAL}, sem exceção</b> (§9.1). Um contrato
 * excedido tem impacto financeiro direto e um anexo infectado é incidente de segurança: permitir
 * silenciá-los contrariaria o propósito do produto.
 *
 * <p><b>Os tipos de consumo não estão aqui.</b> Eles derivam de {@code
 * contract.notificationThresholds} e são identificados por {@link #CONTRACT_USAGE} mais o limiar no
 * {@code dedupeKey} (RN-603). Fixar {@code CONTRACT_USAGE_50/80/100} como valores do enum quebraria
 * um contrato configurado com {@code [70, 90]} — exatamente o que CP-05 proíbe.
 *
 * <p>Alguns valores ainda não possuem produtor: {@code EXPORT_*} chega com {@code 012-reports},
 * {@code ATTACHMENT_INFECTED} com {@code 015-attachments} e {@code MEMBER_*} com {@code 002-users}.
 * Declará-los agora mantém a tela de preferências completa e o catálogo em um lugar só.
 */
@Getter
public enum NotificationType {

    // ── Consumo e saldo ──────────────────────────────────────────────────────────────────────
    /** RN-602: limiar de consumo atingido. A severidade real depende do limiar (ver §6.1). */
    CONTRACT_USAGE(INFO, "Consumo do contrato", true),
    /** RN-604: consumo acima de 100%. Impacto financeiro direto — não silenciável. */
    CONTRACT_OVERAGE(CRITICAL, "Contrato excedido", false),
    /** RN-215: ajuste manual de saldo aplicado por outra pessoa. */
    ADJUSTMENT_APPLIED(INFO, "Ajuste de saldo aplicado", true),

    // ── Períodos e contratos ─────────────────────────────────────────────────────────────────
    /** RN-605: 3 dias antes do fim do período. */
    PERIOD_CLOSING(INFO, "Período próximo do fechamento", true),
    /** RN-241: fechamento concluído. */
    PERIOD_CLOSED(INFO, "Período fechado", true),
    /** RN-242: um relatório já entregue foi reaberto — sempre relevante para quem o recebeu. */
    PERIOD_REOPENED(WARNING, "Período reaberto", true),
    /** RN-606: 15 dias antes do fim do contrato. */
    CONTRACT_ENDING(WARNING, "Contrato próximo do fim", true),

    // ── Cronômetro ───────────────────────────────────────────────────────────────────────────
    /** RN-163: cronômetro além do limiar de execução longa. */
    TIMER_LONG_RUNNING(WARNING, "Cronômetro em execução há muito tempo", true),
    /** RN-164: cronômetro abandonado; recuperável por 7 dias. */
    TIMER_ABANDONED(WARNING, "Cronômetro abandonado", true),
    /** OWN-05: cronômetro encerrado por um administrador — o dono precisa saber. */
    TIMER_FORCE_STOPPED(WARNING, "Cronômetro encerrado por um administrador", true),

    // ── Tickets ──────────────────────────────────────────────────────────────────────────────
    TICKET_ASSIGNED(INFO, "Ticket atribuído a você", true),
    /** RN-312: ticket concluído voltou a receber horas. */
    TICKET_REOPENED(INFO, "Ticket reaberto", true),
    TICKET_COMMENTED(INFO, "Novo comentário no ticket", true),
    /** CE-N-07: mais específico prevalece sobre {@link #TICKET_COMMENTED}. */
    TICKET_MENTIONED(INFO, "Você foi mencionado", true),

    // ── Sem produtor no MVP ──────────────────────────────────────────────────────────────────
    /** {@code 002-users}. */
    MEMBER_JOINED(INFO, "Novo membro na organização", true),
    /** {@code 002-users}. */
    MEMBER_REMOVED(INFO, "Membro removido da organização", true),
    /** {@code 012-reports}. */
    EXPORT_COMPLETED(INFO, "Exportação concluída", true),
    /** {@code 012-reports}. */
    EXPORT_FAILED(WARNING, "Exportação falhou", true),
    /** RN-803, {@code 015-attachments}: incidente de segurança — não silenciável. */
    ATTACHMENT_INFECTED(CRITICAL, "Ameaça detectada em anexo", false);

    private final NotificationSeverity defaultSeverity;
    private final String label;

    /** §9.1: notificação crítica nunca pode ser silenciada. */
    private final boolean canMute;

    NotificationType(NotificationSeverity defaultSeverity, String label, boolean canMute) {
        this.defaultSeverity = defaultSeverity;
        this.label = label;
        this.canMute = canMute;
    }

    /**
     * Tipo pelo nome, sem lançar.
     *
     * <p>Usado na validação de {@code mutedNotificationTypes}: um nome desconhecido produz {@code
     * DEVTIME-2000} com o campo apontado, e não uma exceção de conversão de enum sem contexto.
     */
    public static Optional<NotificationType> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(name.strip()))
                .findFirst();
    }
}
