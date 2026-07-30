package com.devtime.shared.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publicação de eventos de domínio (backend.md §10).
 *
 * <p>Existe como fachada, e não como uso direto de {@link ApplicationEventPublisher}, para que os
 * serviços dependam do tipo {@link DomainEvent} — o que permite ao ArchUnit verificar BR-180/BR-181
 * em um ponto único, e evita que qualquer objeto seja publicado como evento.
 *
 * <p>SV-06 / BR-182: o evento é publicado <b>após</b> a persistência, nunca antes. Quem escolhe
 * entre {@code @EventListener} (atômico) e {@code @TransactionalEventListener(AFTER_COMMIT)}
 * (efeito colateral) é o consumidor, conforme BR-183.
 */
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
