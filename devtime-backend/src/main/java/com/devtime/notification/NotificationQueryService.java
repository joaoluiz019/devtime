package com.devtime.notification;

import com.devtime.notification.dto.NotificationRequests.NotificationFilter;
import com.devtime.notification.dto.NotificationRequests.NotificationPreferencesRequest;
import com.devtime.notification.dto.NotificationResponses.MarkAllReadResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationPreferencesResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationReadResponse;
import com.devtime.notification.dto.NotificationResponses.NotificationResponse;
import com.devtime.notification.dto.NotificationResponses.UnreadCountResponse;
import com.devtime.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Central de notificações do usuário autenticado (spec 013 §22.2).
 *
 * <p><b>Toda operação é restrita ao {@code recipientId} do token</b> (§16). Não há papel que
 * enxergue notificações de terceiros — nem {@code OWNER}. A notificação é dirigida a uma pessoa, e
 * ler a de outra não tem finalidade legítima: a informação subjacente está nas telas de origem, com
 * o controle de acesso adequado.
 */
public interface NotificationQueryService {

    /** §7: listagem paginada, ordenada por {@code createdAt DESC} — ordenação fixa. */
    PageResponse<NotificationResponse> search(NotificationFilter filter, Pageable pageable);

    /** §7.1: endpoint leve, consultado ao carregar toda tela. */
    UnreadCountResponse unreadCount();

    /** §8.1: idempotente — marcar como lida algo já lido não altera {@code readAt}. */
    NotificationReadResponse markRead(UUID id);

    /** FA-13: volta a não lida. */
    NotificationReadResponse markUnread(UUID id);

    /** §8.2 / FA-12. */
    MarkAllReadResponse markAllRead();

    /** FA-14: exclusão lógica; sai da central (RN-003). */
    void delete(UUID id);

    /** §9.1: preferências e o catálogo de tipos disponíveis. */
    NotificationPreferencesResponse preferences();

    /**
     * §9.2: atualização parcial.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-4001} ao silenciar um
     *     tipo crítico; {@code DEVTIME-2000} para um tipo desconhecido
     */
    NotificationPreferencesResponse updatePreferences(NotificationPreferencesRequest request);
}
