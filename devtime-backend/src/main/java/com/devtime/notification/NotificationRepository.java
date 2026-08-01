package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link Notification} (spec 013 §25).
 *
 * <p>A inserção idempotente de RN-601 <b>não</b> vive aqui: ela precisa capturar a violação do
 * índice único, e o tratamento fica em {@code NotificationServiceImpl}, junto da transação que o
 * torna recuperável. O que este repositório oferece são as consultas.
 */
@Repository
public interface NotificationRepository extends SoftDeleteRepository<Notification> {

    /**
     * SG-01 / SG-05: leitura sempre restrita ao destinatário.
     *
     * <p>O {@code recipientId} vem do token, nunca do caminho. Uma notificação de outro usuário é
     * indistinguível de inexistente — {@code 404}, jamais {@code 403} (§12 de notifications.md).
     */
    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.recipientId = :recipientId")
    Optional<Notification> findByIdAndRecipient(
            @Param("id") UUID id, @Param("recipientId") UUID recipientId);

    /**
     * Contagem de não lidas — recai sobre {@code idx_notifications_unread}, índice <b>parcial</b>.
     *
     * <p>É consultada ao carregar toda tela. O índice parcial contém apenas as não lidas: num
     * usuário com 5.000 notificações e 3 não lidas, ele tem 3 entradas.
     */
    @Query(
            """
            SELECT COUNT(n) FROM Notification n
             WHERE n.recipientId = :recipientId
               AND n.readAt IS NULL
            """)
    long countUnread(@Param("recipientId") UUID recipientId);

    /** §7.1: quebra da contagem por severidade, exibida no indicador do cabeçalho. */
    @Query(
            """
            SELECT n.severity AS severity, COUNT(n) AS total FROM Notification n
             WHERE n.recipientId = :recipientId
               AND n.readAt IS NULL
             GROUP BY n.severity
            """)
    List<SeverityCount> countUnreadBySeverity(@Param("recipientId") UUID recipientId);

    /** Projeção da contagem por severidade — nunca a entidade (BR-107). */
    interface SeverityCount {
        com.devtime.notification.domain.NotificationSeverity getSeverity();

        long getTotal();
    }

    /**
     * FA-12: marca como lidas todas as não lidas do destinatário, em <b>uma</b> instrução.
     *
     * <p>Carregar 5.000 entidades para chamar um setter em cada uma faria a operação proporcional
     * ao histórico do usuário — e ela existe justamente para quem acumulou muitas.
     *
     * @return quantidade marcada, devolvida na resposta (§8.2)
     */
    @Modifying
    @Query(
            """
            UPDATE Notification n
               SET n.readAt = :readAt,
                   n.updatedAt = :readAt
             WHERE n.recipientId = :recipientId
               AND n.readAt IS NULL
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    /**
     * RN-610: fila de reprocessamento de e-mail, em <b>todos</b> os tenants.
     *
     * <p>Consulta de job de plataforma. O limite de três tentativas está no predicado, não no
     * consumidor: uma quarta tentativa é proibida (CP-08), e deixar o corte na consulta impede que
     * um erro no job a produza.
     */
    @Query(
            """
            SELECT n FROM Notification n
             WHERE n.emailSentAt IS NULL
               AND n.emailAttempts > 0
               AND n.emailAttempts < 3
             ORDER BY n.createdAt ASC
            """)
    List<Notification> findPendingEmail(org.springframework.data.domain.Pageable pageable);

    /**
     * RN-609: notificações <b>lidas</b> há mais que o limite.
     *
     * <p>CX-16: o corte é estritamente maior — lida há exatamente 90 dias permanece. CX-17: uma
     * notificação <b>não lida</b> nunca é selecionada, por mais antiga que seja; purgar um alerta
     * que ninguém viu esconderia a informação de que ele existiu.
     */
    @Query(
            """
            SELECT n FROM Notification n
             WHERE n.readAt IS NOT NULL
               AND n.readAt < :threshold
            """)
    List<Notification> findPurgeable(@Param("threshold") Instant threshold);

    /**
     * RN-609: remoção <b>física</b> na purga.
     *
     * <p>É a única exclusão física de entidade de domínio no sistema, e a exceção é deliberada.
     * RN-003 torna lógica a exclusão <b>feita pelo usuário</b> — o estado "Excluída" de §10, que
     * apenas remove a notificação da central. A purga é outra coisa: §19.1 fixa a retenção em "até
     * 90 dias após a leitura", a política mais curta do sistema, e uma exclusão lógica manteria o
     * dado indefinidamente, descumprindo-a. O fato subjacente permanece auditado na feature de
     * origem, com retenção própria.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids")
    int purge(@Param("ids") java.util.Collection<UUID> ids);
}
