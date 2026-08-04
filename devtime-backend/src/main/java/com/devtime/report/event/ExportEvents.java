package com.devtime.report.event;

import com.devtime.shared.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Eventos da exportação (§15 de specs/012).
 *
 * <p>BR-181: carregam identificadores, nunca entidades. E <b>nunca</b> carregam filtros nem
 * conteúdo de linha: o consumidor é {@code 013-notifications}, que os transforma em texto exibido —
 * §19.1 proíbe que dado pessoal de terceiros chegue a uma notificação de terceiro.
 *
 * <p>Fecham a pendência registrada na nota ¹¹ de {@code implementation-order.md} §12: {@code
 * EXPORT_COMPLETED} e {@code EXPORT_FAILED} estavam declarados no catálogo de {@code 013} sem
 * produtor, aguardando esta feature.
 */
public final class ExportEvents {

    private ExportEvents() {}

    /**
     * A exportação assíncrona foi enfileirada.
     *
     * <p>Existe apesar de o {@code ExportProcessorJob} varrer a tabela por conta própria: o evento
     * é o que permite ao worker assumir a execução no próximo ciclo em vez de esperar o intervalo
     * completo, e a varredura continua sendo a rede de segurança para o caso de o evento se perder
     * (BR-185: o job é convergente).
     */
    public record ExportRequestedEvent(UUID executionId, UUID requestedBy, Instant requestedAt)
            implements DomainEvent {}

    /** §15: notifica o solicitante (FA-10). */
    public record ExportCompletedEvent(
            UUID executionId, UUID requestedBy, String format, int rowCount)
            implements DomainEvent {}

    /**
     * §15: notifica com o motivo.
     *
     * @param failureReason texto já traduzido para o usuário; nunca mensagem crua de exceção nem
     *     SQL (BR-092, CE-R-11)
     */
    public record ExportFailedEvent(
            UUID executionId, UUID requestedBy, String format, String failureReason)
            implements DomainEvent {}
}
