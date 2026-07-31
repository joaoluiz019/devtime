package com.devtime.auth;

import com.devtime.auth.AuthService.RequestMetadata;
import com.devtime.auth.AuthService.SessionOutcome;
import com.devtime.auth.dto.AuthRequests.AcceptInvitationRequest;
import com.devtime.auth.dto.AuthResponses.InvitationResponse;
import java.util.UUID;

/**
 * Consulta e aceite de convite (RN-457, §5.12, T-001-30).
 *
 * <p>Apenas o <b>consumo</b> do convite está aqui. A emissão pertence a {@code 002-users} (OB-07 do
 * spec): trazê-la para cá faria a feature de sessão depender de gestão de membros.
 */
public interface InvitationAcceptanceService {

    /**
     * Dados públicos do convite, para a tela de aceite.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2457} quando expirado;
     *     {@code DEVTIME-2458} quando desconhecido ou revogado
     */
    InvitationResponse peek(String rawToken);

    /**
     * Aceita o convite, ativando o vínculo (§5.12).
     *
     * <p>CX-09 / AC-001-30: quando o usuário já está autenticado em outra organização, a sessão
     * corrente <b>não</b> troca de tenant — a organização apenas passa a aparecer no seletor.
     *
     * @param authenticatedUserId usuário da sessão corrente, ou {@code null} quando o aceite parte
     *     de alguém não autenticado
     * @return sessão emitida quando o aceite também autentica; {@code null} quando o usuário já
     *     estava autenticado e a sessão corrente é preservada
     */
    SessionOutcome accept(
            String rawToken,
            AcceptInvitationRequest request,
            UUID authenticatedUserId,
            RequestMetadata metadata);
}
