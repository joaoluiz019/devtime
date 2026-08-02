package com.devtime.tenant;

import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.mail.MailMessage;
import com.devtime.shared.mail.MailMessage.MailTemplate;
import com.devtime.shared.mail.MailPort;
import com.devtime.tenant.event.TenantEvents.MemberInvitedEvent;
import com.devtime.user.UserAccountService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * E-mail de convite (FA-06, spec §15).
 *
 * <p>{@code AFTER_COMMIT} (BR-128): a indisponibilidade do provedor não pode desfazer o convite já
 * registrado — o vínculo {@code INVITED} existe, e o reenvio (FA-07) é o caminho de recuperação.
 *
 * <p>O template já existia em {@code mail/invitation.html}, escrito por {@code 001} para o lado do
 * aceite; a emissão, que é desta feature, apenas passou a alimentá-lo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberMailNotifier {

    private final MailPort mailPort;
    private final UserAccountService userAccountService;
    private final DevTimeProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberInvited(MemberInvitedEvent event) {
        var invited = userAccountService.findById(event.invitedUserId()).orElse(null);
        if (invited == null) {
            return;
        }
        String invitedByName =
                userAccountService
                        .findById(event.invitedBy())
                        .map(account -> account.fullName())
                        .orElse("A organização");
        boolean sent =
                mailPort.send(
                        new MailMessage(
                                invited.email(),
                                MailTemplate.INVITATION,
                                Map.of(
                                        "invitedByName", invitedByName,
                                        "tenantName", event.tenantName(),
                                        "role", event.role().name(),
                                        "invitationUrl",
                                                link("/auth/invitations", event.rawToken()))));
        if (!sent) {
            // ART-084 / CP-12: o endereço não entra no log; o identificador do vínculo basta para
            // diagnosticar e para acionar o reenvio.
            log.warn("falha ao enviar convite membershipId={}", event.membershipId());
        }
    }

    private String link(String path, String rawToken) {
        return properties.app().baseUrl()
                + path
                + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
