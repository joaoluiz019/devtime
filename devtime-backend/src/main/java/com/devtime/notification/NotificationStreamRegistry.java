package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.notification.domain.NotificationExceptions;
import com.devtime.notification.dto.NotificationResponses.StreamEventDto;
import com.devtime.shared.time.TenantClock;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registro de conexões e publicação no fluxo SSE (§7.2 de notifications.md).
 *
 * <p><b>Não é um {@code *Service}, e a divergência com §22.2 da spec é deliberada.</b> BR-069
 * proíbe que um serviço conheça a camada HTTP, e {@link SseEmitter} é um tipo web — um serviço que
 * o carregasse deixaria de ser reusável por job ou por outra feature. Este é o <b>adaptador</b> da
 * borda web: ele vive ao lado do controller, e o que a feature expõe como serviço são {@code
 * NotificationService} e {@code NotificationQueryService}.
 *
 * <p><b>ST-05 / INV-NOT-04: o fluxo nunca é o único canal.</b> Ele é otimização de latência; o
 * histórico em {@code GET /notifications} é sempre a fonte da verdade. Uma queda de conexão, um
 * cliente desconectado ou uma falha de publicação não perdem notificação alguma.
 *
 * <p>SG-03: o registro é por {@code recipientId}, <b>nunca por tenant</b> — um fluxo por tenant
 * entregaria a cada conectado as notificações de todos os colegas.
 *
 * <p>O registro é <b>em memória</b>, e isso é a dívida declarada em OB-08: com o deploy de
 * instância única previsto em §10 de {@code architecture.md}, é suficiente. Ao distribuir em
 * múltiplas instâncias, a publicação precisaria alcançar a instância onde o usuário está conectado,
 * e o registro teria de sair da memória — mas nenhuma notificação se perderia mesmo assim, porque
 * ST-05 garante que o histórico é a fonte.
 *
 * <p>Toda falha de publicação é engolida por decisão: a notificação já está persistida, e uma
 * exceção aqui interromperia a criação das notificações dos destinatários seguintes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationStreamRegistry {

    /** ST-03. */
    public static final int MAX_CONNECTIONS_PER_USER = 3;

    /** ST-02: a conexão expira junto com o access token, e o cliente reconecta ao renová-lo. */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(15);

    private static final String EVENT_NOTIFICATION = "notification";
    private static final String EVENT_UNREAD_COUNT = "unread-count";
    private static final String EVENT_HEARTBEAT = "heartbeat";

    /** SG-03: chave é o destinatário, nunca o tenant. */
    private final Map<UUID, List<SseEmitter>> connections = new ConcurrentHashMap<>();

    private final TenantClock clock;

    /**
     * ST-03: no máximo três conexões simultâneas por usuário — abas em paralelo são normais,
     * dezenas não são.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-4003} / {@code 429}
     */
    public SseEmitter open(UUID recipientId) {
        List<SseEmitter> emitters =
                connections.computeIfAbsent(recipientId, key -> new CopyOnWriteArrayList<>());
        if (emitters.size() >= MAX_CONNECTIONS_PER_USER) {
            throw NotificationExceptions.streamLimitReached(MAX_CONNECTIONS_PER_USER); // ST-03
        }

        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
        emitters.add(emitter);
        // Os três encerramentos são registrados: sem eles, um cliente que fecha a aba deixaria o
        // emissor na lista e consumiria uma das três conexões permitidas para sempre.
        emitter.onCompletion(() -> remove(recipientId, emitter));
        emitter.onTimeout(() -> remove(recipientId, emitter));
        emitter.onError(error -> remove(recipientId, emitter));

        log.debug("conexão SSE aberta userId={} conexões={}", recipientId, emitters.size());
        return emitter;
    }

    /** Publica no fluxo do destinatário, se houver algum conectado. Nunca lança. */
    public void publish(Notification notification, long unreadCount) {
        List<SseEmitter> emitters = connections.get(notification.getRecipientId());
        if (emitters == null || emitters.isEmpty()) {
            // FA-09: usuário offline. A notificação foi criada normalmente e será vista ao abrir a
            // central — o fluxo é otimização, não entrega.
            return;
        }
        StreamEventDto event =
                new StreamEventDto(
                        notification.getId(),
                        notification.getType().name(),
                        notification.getSeverity().name(),
                        notification.getTitle(),
                        unreadCount);
        emitters.forEach(
                emitter -> {
                    send(notification.getRecipientId(), emitter, EVENT_NOTIFICATION, event);
                    send(
                            notification.getRecipientId(),
                            emitter,
                            EVENT_UNREAD_COUNT,
                            Map.of("unreadCount", unreadCount));
                });
    }

    /** ST-01: sem o heartbeat, proxies intermediários encerram conexões ociosas. */
    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeats() {
        if (connections.isEmpty()) {
            return;
        }
        Map<String, String> payload = Map.of("serverTime", clock.now().toString());
        connections.forEach(
                (recipientId, emitters) ->
                        emitters.forEach(
                                emitter -> send(recipientId, emitter, EVENT_HEARTBEAT, payload)));
    }

    private void send(UUID recipientId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException disconnected) {
            // A desconexão é o caso normal, não excepcional: o cliente fecha a aba, a rede cai, o
            // proxy encerra. Remover e seguir é o comportamento correto — nada se perde (ST-05).
            remove(recipientId, emitter);
        }
    }

    private void remove(UUID recipientId, SseEmitter emitter) {
        List<SseEmitter> emitters = connections.get(recipientId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            connections.remove(recipientId);
        }
        log.debug("conexão SSE encerrada userId={}", recipientId);
    }
}
