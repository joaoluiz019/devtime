package com.devtime.notification.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Mensagem gerada por um evento de domínio, dirigida a uma pessoa (entities.md §6.18).
 *
 * <p><b>Não possui campo {@code status}.</b> Todos os estados observáveis derivam de {@code
 * readAt}, {@code emailSentAt} e {@code deletedAt}; um enum de situação duplicaria informação já
 * presente nesses três campos e criaria a possibilidade de eles divergirem.
 *
 * <p>O {@code dedupeKey} é o coração da feature (RN-601): ele identifica o <b>evento lógico</b>, e
 * o índice único {@code (recipient_id, dedupe_key)} transforma "não notificar duas vezes" em
 * garantia estrutural. Sem ele, o consumo oscilando em torno de 80% por edições de work log geraria
 * uma notificação por oscilação — e o usuário desligaria as notificações inteiras.
 */
@Entity
@Table(name = "notifications")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Notification extends TenantScopedEntity {

    /** RN-610: limite de tentativas de envio; a quarta é proibida (CP-08). */
    public static final short MAX_EMAIL_ATTEMPTS = 3;

    /**
     * 🔒 RN-607: o destinatário é resolvido na <b>criação</b>, nunca na leitura.
     *
     * <p>CX-10: quem foi promovido a {@code ADMIN} depois do alerta não o recebe retroativamente —
     * a notificação registra quem precisava saber naquele momento.
     */
    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private NotificationSeverity severity;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    /** §19.1: sem descrições de work log e sem valores monetários — o e-mail sai do tenant. */
    @Column(name = "body", nullable = false, length = 500)
    private String body;

    /** Dados estruturados para renderização rica; nunca contém dado sensível. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    /** Origem da notificação, usada para montar a rota de navegação (NT-03). */
    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    /** 🔒 RN-601 / INV-NOT-01: único por destinatário. CP-11: nunca exposto na API. */
    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 200)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    /** INV-NOT-05: preenchido no envio; nulo nunca reverte a notificação. */
    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    /** RN-610: contador de tentativas, base do limite de três. */
    @Column(name = "email_attempts", nullable = false)
    private short emailAttempts;

    public boolean isRead() {
        return readAt != null;
    }

    /** RN-610: ainda há tentativa disponível e o e-mail não foi entregue. */
    public boolean isEmailPending() {
        return emailSentAt == null && emailAttempts < MAX_EMAIL_ATTEMPTS;
    }
}
