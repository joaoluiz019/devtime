package com.devtime.notification;

import com.devtime.notification.domain.Notification;
import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.mail.MailMessage;
import com.devtime.shared.mail.MailPort;
import com.devtime.shared.time.TenantClock;
import com.devtime.user.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrega por e-mail (ver {@link EmailDispatchService}).
 *
 * <p><b>O e-mail nunca reverte a notificação</b> (RN-610, INV-NOT-05). {@link MailPort} não lança —
 * devolve {@code false} em qualquer falha —, o que torna a degradação estrutural em vez de depender
 * de um {@code try/catch} que alguém pode remover.
 *
 * <p>O contador de tentativas é incrementado <b>antes</b> do envio. Se o processo cair no meio da
 * chamada ao provedor, a tentativa já está registrada: preferir contar a mais a contar a menos é o
 * que garante o limite de três (CP-08) mesmo com falha de infraestrutura.
 *
 * <p>§28 / CP-18: <b>nem o endereço de e-mail nem o conteúdo entram em log</b>.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EmailDispatchServiceImpl implements EmailDispatchService {

    private final MailPort mailPort;
    private final EmailDispatchPolicy dispatchPolicy;
    private final NotificationRouteResolver routeResolver;
    private final UserService userService;
    private final DevTimeProperties properties;
    private final TenantClock clock;

    @Override
    @Transactional
    public void dispatch(Notification notification) {
        EmailDispatchPolicy.Decision decision =
                dispatchPolicy.evaluate(notification.getRecipientId(), notification.getType());
        if (!decision.allowed()) {
            // FA-06 / FA-07: nenhum e-mail, e a in-app permanece. Não é falha — é a preferência
            // do destinatário sendo respeitada.
            log.debug(
                    "e-mail suprimido notificationId={} type={} reason={}",
                    notification.getId(),
                    notification.getType(),
                    decision.reason());
            return;
        }
        send(notification, decision.emailAddress());
    }

    @Override
    @Transactional
    public boolean retry(Notification notification) {
        if (!notification.isEmailPending()) {
            // CP-08: já entregue ou já esgotado. O predicado da consulta do job também impede
            // chegar aqui, e esta verificação é a segunda barreira.
            return false;
        }
        EmailDispatchPolicy.Decision decision =
                dispatchPolicy.evaluate(notification.getRecipientId(), notification.getType());
        if (!decision.allowed()) {
            // CX-12: o tipo foi silenciado depois da criação. A notificação permanece; o e-mail
            // pendente deixa de ser tentado, e marcar as tentativas como esgotadas evita que ele
            // volte à fila a cada execução do job.
            notification.setEmailAttempts(Notification.MAX_EMAIL_ATTEMPTS);
            return false;
        }
        return send(notification, decision.emailAddress());
    }

    private boolean send(Notification notification, String emailAddress) {
        // Incrementa antes de enviar: uma queda no meio da chamada ao provedor deixaria a
        // tentativa não contabilizada, e o limite de três deixaria de valer.
        notification.setEmailAttempts((short) (notification.getEmailAttempts() + 1));

        boolean accepted =
                mailPort.send(
                        new MailMessage(
                                emailAddress,
                                MailMessage.MailTemplate.NOTIFICATION,
                                variables(notification)));

        if (accepted) {
            notification.setEmailSentAt(clock.now());
            log.info(
                    "e-mail de notificação enviado notificationId={} type={}",
                    notification.getId(),
                    notification.getType());
            return true;
        }

        if (notification.getEmailAttempts() >= Notification.MAX_EMAIL_ATTEMPTS) {
            // ERROR: três falhas significam que o destinatário não foi alcançado por este canal.
            // A in-app continua lá — é o que impede a informação de se perder (INV-NOT-05).
            log.error(
                    "e-mail de notificação esgotou as tentativas notificationId={} type={}",
                    notification.getId(),
                    notification.getType());
        } else {
            log.warn(
                    "falha no envio de e-mail de notificação notificationId={} attempt={}",
                    notification.getId(),
                    notification.getEmailAttempts());
        }
        return false;
    }

    /**
     * ML-08 / §19.1: apenas o estritamente necessário.
     *
     * <p>{@code title} e {@code body} já foram montados sem descrições de work log e sem valores
     * monetários; o {@code payload} da notificação <b>não</b> viaja para o e-mail.
     */
    private Map<String, String> variables(Notification notification) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("recipientName", userService.summaryOf(notification.getRecipientId()).name());
        variables.put("title", notification.getTitle());
        variables.put("body", notification.getBody());
        variables.put("typeLabel", notification.getType().getLabel());
        variables.put(
                "actionUrl",
                properties.app().baseUrl() + routeResolver.resolve(notification).route());
        return variables;
    }
}
