package com.devtime.worklog.event;

import com.devtime.shared.event.DomainEvent;
import java.util.UUID;

/**
 * Eventos da feature 008 (spec §15).
 *
 * <p>BR-180/BR-181: {@code record} imutável com identificadores, nunca entidades.
 *
 * <p><b>Os três desnormalizados não viajam por evento</b> (OB-06): {@code ticket.spentMinutes},
 * {@code period.consumedMinutes} e o status do ticket são atualizados por chamada direta,
 * <b>dentro</b> da transação. A resposta {@code 201} já devolve o saldo atualizado, e um saldo
 * desatualizado no exato momento do registro destruiria a confiança no número que é o produto.
 * Estes eventos servem ao que <b>pode</b> ser assíncrono: avaliação de limiares em {@code 013} e
 * telemetria, ambos após o commit (TX-06, BR-128).
 */
public final class WorkLogEvents {

    private WorkLogEvents() {}

    /** RN-602: {@code 013-notifications} avalia os limiares de consumo do período. */
    public record WorkLogCreatedEvent(
            UUID workLogId, UUID contractPeriodId, UUID ticketId, int billableMinutes)
            implements DomainEvent {}

    /** Reavaliação após edição; o delta permite saber a direção do cruzamento do limiar. */
    public record WorkLogUpdatedEvent(
            UUID workLogId, UUID contractPeriodId, UUID ticketId, int billableMinutesDelta)
            implements DomainEvent {}

    /**
     * CE-11 / CX-20: a exclusão reavalia, mas <b>não</b> remove a notificação anterior.
     *
     * <p>O limiar chegou a ser cruzado; apagar o registro disso esconderia um fato que já foi
     * comunicado ao cliente. O {@code dedupeKey} de {@code 013} impede um alerta novo se o consumo
     * voltar a subir.
     */
    public record WorkLogDeletedEvent(
            UUID workLogId, UUID contractPeriodId, UUID ticketId, int billableMinutesDelta)
            implements DomainEvent {}
}
