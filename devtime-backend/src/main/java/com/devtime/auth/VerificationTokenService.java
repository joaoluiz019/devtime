package com.devtime.auth;

import com.devtime.auth.domain.VerificationTokenType;
import java.time.Instant;
import java.util.UUID;

/**
 * Emissão e consumo dos tokens de uso único (T-001-21).
 *
 * <p>Cobre os três fluxos: verificação de e-mail, redefinição de senha e convite. A validade de
 * cada tipo é decidida aqui, e não por quem chama, para que RN-457 (7 dias) e RN-461 (1 hora)
 * tenham um único ponto de verdade.
 */
public interface VerificationTokenService {

    /** Token emitido: o valor bruto existe apenas neste retorno. */
    record IssuedToken(UUID id, String rawToken, Instant expiresAt) {}

    /** Token resolvido: consumido, ou apenas inspecionado por {@link #peek}. */
    record ConsumedToken(UUID id, UUID userId, UUID tenantId, Instant expiresAt) {}

    /**
     * Emite um token, invalidando o anterior do mesmo usuário e tipo (RN-457).
     *
     * @param tenantId obrigatório em {@link VerificationTokenType#INVITATION}, nulo nos demais
     */
    IssuedToken issue(UUID userId, UUID tenantId, VerificationTokenType type);

    /**
     * Consome o token, marcando-o como usado na mesma transação do efeito (RN-461, SG-13).
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-1009}/{@code 1010} para
     *     verificação, {@code DEVTIME-1007} para redefinição e {@code DEVTIME-2457}/{@code 2458}
     *     para convite — cada fluxo tem código próprio por exigência de {@code authentication.md}
     *     §5.6, §5.8 e §5.12
     */
    ConsumedToken consume(String rawToken, VerificationTokenType type);

    /**
     * Lê o token sem consumi-lo, para telas de confirmação ({@code GET /auth/invitations/token}).
     *
     * @throws com.devtime.shared.error.BusinessRuleException nas mesmas condições de {@link
     *     #consume}
     */
    ConsumedToken peek(String rawToken, VerificationTokenType type);

    /**
     * CE-AU-04: devolve o token quando ele já foi consumido, sem lançar.
     *
     * <p>Existe exclusivamente para a idempotência da verificação de e-mail: {@code
     * authentication.md} §5.6 e CA-08 exigem que a segunda chamada com o mesmo link responda
     * sucesso, porque clientes de e-mail com pré-visualização consomem o link antes do usuário.
     *
     * <p>Um token consumido e <b>expirado</b> não é devolvido: a idempotência vale para o clique
     * repetido em um link recente, não para ressuscitar um link vencido há semanas.
     */
    java.util.Optional<ConsumedToken> findConsumed(String rawToken, VerificationTokenType type);

    /** {@code VerificationTokenCleanupJob}: descarta consumidos e expirados (T-001-36). */
    int purgeSettled();
}
