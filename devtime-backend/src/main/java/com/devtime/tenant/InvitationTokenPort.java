package com.devtime.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * Emissão do token de convite (RN-457).
 *
 * <p>Outra inversão de dependência (AR-03): o token de uso único pertence a {@code
 * 001-authentication}, que já depende de {@code tenant} — o aceite do convite é dele. Chamar {@code
 * VerificationTokenService} daqui fecharia o ciclo {@code tenant → auth → tenant} (BR-008).
 *
 * <p>A divisão de responsabilidade segue §4 da spec: a <b>emissão</b> é autenticada e pertence a
 * {@code 002}; o <b>aceite</b> é público e pertence a {@code 001}. O token, por ser o mesmo
 * artefato dos fluxos de verificação e redefinição, continua vivendo em {@code 001}.
 */
public interface InvitationTokenPort {

    /**
     * Emite o token, invalidando o anterior do mesmo usuário (RN-457: o reenvio invalida o
     * anterior).
     *
     * @param rawToken valor bruto; existe apenas neste retorno e segue para o e-mail
     */
    record IssuedInvitation(String rawToken, Instant expiresAt) {}

    IssuedInvitation issue(UUID userId, UUID tenantId);
}
