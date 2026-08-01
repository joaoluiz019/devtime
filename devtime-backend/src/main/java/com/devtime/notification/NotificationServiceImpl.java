package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.notification.dto.NotificationCommand;
import com.devtime.shared.time.TenantClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação de notificações (ver {@link NotificationService}).
 *
 * <p><b>A ordem da §6.2 é normativa.</b> A in-app é criada <b>antes</b> de qualquer decisão sobre
 * e-mail, e isso não é organização de código: RN-610 exige que a falha de e-mail não impeça a
 * notificação, e criar primeiro garante isso <b>estruturalmente</b> — se o envio falhar, o registro
 * já existe.
 *
 * <p><b>Por que a inserção é tentada sem verificação prévia</b> (CP-03): verificar a existência
 * antes de inserir abriria uma janela de corrida entre a verificação e a inserção — exatamente o
 * cenário de duas avaliações concorrentes do mesmo limiar. O índice único {@code (recipient_id,
 * dedupe_key)} decide, e a violação é tratada como sucesso silencioso.
 *
 * <p>Cada destinatário é inserido em <b>transação própria</b> ({@code REQUIRES_NEW}). Sem isso, a
 * violação do índice marcaria a transação inteira como {@code rollback-only} e o segundo
 * destinatário — que talvez não tivesse a chave — deixaria de ser notificado por causa do primeiro.
 *
 * <p>§28 / CP-18: <b>{@code title}, {@code body} e {@code payload} nunca entram em log</b>. A
 * criação é registrada em {@code DEBUG}, não {@code INFO}: a avaliação de limiares roda em toda
 * alteração de consumo, e registrar cada uma inundaria o log.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;
    private final NotificationInserter inserter;
    private final NotificationStreamRegistry streamRegistry;
    private final EmailDispatchService emailDispatchService;
    private final ObjectMapper objectMapper;
    private final TenantClock clock;

    @Override
    public int notify(NotificationCommand command) {
        // Passo 1 — nenhum destinatário: nada é criado, sem erro (FA-05).
        if (command.recipientIds().isEmpty()) {
            return 0;
        }

        String payload = serialize(command.payload());
        int created = 0;

        for (UUID recipientId : command.recipientIds()) {
            // Passos 3 e 4 — inserção idempotente; a in-app é criada SEMPRE (RN-608).
            String dedupeKey = command.dedupeKeyFor().apply(recipientId);
            Notification notification =
                    inserter.insertIgnoringDuplicate(command, recipientId, payload, dedupeKey);
            if (notification == null) {
                // RN-601: chave já existente. Comportamento normal, não erro (CP-02).
                log.debug(
                        "notificação duplicada ignorada recipientId={} dedupeKey={}",
                        recipientId,
                        dedupeKey);
                continue;
            }
            created++;
            log.debug(
                    "notificação criada recipientId={} type={} dedupeKey={}",
                    recipientId,
                    command.type(),
                    dedupeKey);

            // Passo 5 — publicação no fluxo. ST-05: falha aqui não impede nada, porque o fluxo
            // nunca é o único canal.
            streamRegistry.publish(notification, repository.countUnread(recipientId));

            // Passos 6 e 7 — preferências e enfileiramento. Depois da in-app, sempre.
            emailDispatchService.dispatch(notification);
        }
        return created;
    }

    /** O instante é do relógio injetado (BR-141); usado pelos testes de retenção. */
    java.time.Instant now() {
        return clock.now();
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            // O payload é montado pelo próprio sistema; falha aqui é defeito de programação, não
            // condição de execução (CG-06).
            throw new IllegalStateException(
                    "Falha ao serializar o payload da notificação", failure);
        }
    }

    /**
     * Inserção isolada por destinatário.
     *
     * <p>Classe própria porque {@code REQUIRES_NEW} é aplicado por proxy: uma chamada a {@code
     * this.insert(...)} dentro de {@link NotificationServiceImpl} não passaria pelo proxy e
     * herdaria a transação do chamador — que é justamente a que não pode ser marcada como {@code
     * rollback-only} pela violação esperada do índice único.
     */
    @org.springframework.stereotype.Component
    @RequiredArgsConstructor
    static class NotificationInserter {

        private final NotificationRepository repository;

        /**
         * @return a notificação criada, ou {@code null} quando a chave já existia (RN-601)
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        Notification insertIgnoringDuplicate(
                NotificationCommand command, UUID recipientId, String payload, String dedupeKey) {
            Notification notification = new Notification();
            notification.setRecipientId(recipientId);
            notification.setType(command.type());
            notification.setSeverity(command.severity());
            notification.setTitle(command.title());
            notification.setBody(command.body());
            notification.setPayload(payload);
            notification.setEntityType(command.entityType());
            notification.setEntityId(command.entityId());
            notification.setDedupeKey(dedupeKey);
            notification.setEmailAttempts((short) 0);
            try {
                return repository.saveAndFlush(notification);
            } catch (DataIntegrityViolationException duplicate) {
                // CP-03: é o índice único que decide, não uma verificação prévia. A exceção aqui é
                // o caminho esperado, não um erro — RN-601 a define como sucesso silencioso.
                return null;
            }
        }
    }
}
