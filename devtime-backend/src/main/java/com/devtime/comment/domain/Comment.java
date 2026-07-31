package com.devtime.comment.domain;

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
 * Comentário do ticket (entities.md §6.16).
 *
 * <p>Duas naturezas na mesma tabela. O comentário de <b>usuário</b> é conversa: editável pelo autor
 * por 24 horas (RN-812) e moderável por {@code ADMIN}/{@code OWNER}. O comentário de <b>sistema</b>
 * ({@code isSystem = true}) é registro automático de fato ocorrido — imutável e inexcluível
 * (RN-815, INV-CMT-03).
 *
 * <p>INV-CMT-01: {@code parentCommentId} sempre aponta para uma <b>raiz</b>. A hierarquia é
 * normalizada na escrita (RN-814), não na leitura: resolver na escrita mantém a árvore plana por
 * construção, com dois níveis garantidos, em vez de achatá-la a cada consulta.
 */
@Entity
@Table(name = "comments")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Comment extends TenantScopedEntity {

    /** 🔒 alvo do comentário; ausente dos DTOs de atualização (INV-CMT-05). */
    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    /** 🔒 autor; nulo apenas em comentário de sistema, que não tem pessoa a quem atribuir. */
    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    /** RN-811: Markdown entre 1 e 10.000 caracteres após aparar. */
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /** 🔒 INV-CMT-01: sempre uma raiz do mesmo ticket (INV-CMT-02). */
    @Column(name = "parent_comment_id", updatable = false)
    private UUID parentCommentId;

    /** Preenchido na edição; a UI sinaliza "editado" a partir dele. */
    @Column(name = "edited_at")
    private Instant editedAt;

    /**
     * INV-CMT-04: apenas membros ativos no momento da criação (RN-813).
     *
     * <p>CX-07: não é reavaliado depois. Uma menção a quem foi suspenso em seguida permanece
     * registrada — a notificação já enviada é um fato, e reescrever o passado tornaria a trilha
     * inconsistente com o que aconteceu.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "mentioned_user_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] mentionedUserIds = new UUID[0];

    /** 🔒 INV-CMT-03: comentário de sistema é imutável e inexcluível. */
    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    /** Gatilho de RN-815; nulo em comentário de usuário. */
    @Enumerated(EnumType.STRING)
    @Column(name = "system_trigger", length = 40, updatable = false)
    private SystemCommentTrigger systemTrigger;
}
