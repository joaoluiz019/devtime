package com.devtime.auth;

import com.devtime.auth.event.AuthEvents.RefreshTokenReuseDetectedEvent;
import com.devtime.shared.event.DomainEventPublisher;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revogação em cadeia na detecção de reuso (RN-005, RT-04).
 *
 * <p>Componente separado por uma razão estrutural, não estética: a revogação precisa
 * <b>persistir</b> enquanto a requisição termina em erro. Se ela ocorresse na mesma transação de
 * {@code RefreshTokenServiceImpl.rotate}, o {@code 401} lançado logo em seguida faria o rollback
 * desfazer exatamente a proteção que a regra existe para aplicar — e AC-001-31 exige que os tokens
 * fiquem revogados <b>e</b> que a resposta seja {@code 401 DEVTIME-1005}.
 *
 * <p>{@code REQUIRES_NEW} é justificado por CE-B-02 (BR-122): registro de falha. É o único uso
 * desta propagação na feature.
 *
 * <p>Auto-invocação não funcionaria: o proxy transacional do Spring só intercepta chamadas vindas
 * de fora do bean, e um método privado anotado seria silenciosamente ignorado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenReuseRevocationService {

    private final RefreshTokenRepository repository;
    private final DomainEventPublisher events;
    private final AuthMetrics metrics;
    private final Clock clock;

    /**
     * Revoga toda a cadeia do usuário e registra o evento de segurança.
     *
     * @return quantidade de tokens revogados
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeChainOf(UUID userId) {
        int revoked = repository.revokeAllByUserId(userId, clock.instant());
        // §28: ERROR com userId e revokedCount. O valor do token nunca é registrado (CP-11).
        log.error("reuso de refresh token detectado userId={} revokedCount={}", userId, revoked);
        metrics.tokenReuseDetected(); // §29: alerta crítico em qualquer ocorrência
        events.publish(new RefreshTokenReuseDetectedEvent(userId, revoked));
        return revoked;
    }
}
