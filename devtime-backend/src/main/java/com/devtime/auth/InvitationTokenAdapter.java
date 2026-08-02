package com.devtime.auth;

import com.devtime.auth.domain.VerificationTokenType;
import com.devtime.tenant.InvitationTokenPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementação de {@link InvitationTokenPort} sobre {@link VerificationTokenService} (T-002-22).
 *
 * <p>Adaptador fino de propósito: a validade de 7 dias, a invalidação do token anterior e o formato
 * opaco continuam decididos em um único lugar — {@code VerificationTokenService} —, e {@code
 * 002-users} recebe apenas o valor bruto e a expiração de que precisa para o e-mail.
 */
@Component
@RequiredArgsConstructor
public class InvitationTokenAdapter implements InvitationTokenPort {

    private final VerificationTokenService verificationTokenService;

    @Override
    public IssuedInvitation issue(UUID userId, UUID tenantId) {
        var issued =
                verificationTokenService.issue(userId, tenantId, VerificationTokenType.INVITATION);
        return new IssuedInvitation(issued.rawToken(), issued.expiresAt());
    }
}
