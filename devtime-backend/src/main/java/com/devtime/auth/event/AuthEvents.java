package com.devtime.auth.event;

import com.devtime.shared.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Eventos de domínio de {@code 001-authentication} (spec §15).
 *
 * <p>BR-180/BR-181: {@code record} imutável carregando identificadores, nunca entidades.
 *
 * <p><b>Momento de publicação.</b> Todos os eventos deste arquivo são consumidos <b>após o
 * commit</b> (TX-06, CP-10, BR-128): o que eles disparam é envio de e-mail, e a indisponibilidade
 * do provedor não pode desfazer um cadastro, um bloqueio de segurança ou uma troca de senha já
 * decididos. O único efeito que precisa ser atômico — o seed das 9 categorias (RN-501) — é uma
 * chamada direta dentro da transação de provisionamento, exatamente porque um tenant sem categorias
 * impede o primeiro registro de horas (RN-104).
 *
 * <p>O valor bruto do token viaja no evento porque é o único momento em que ele existe: persistido
 * há apenas o SHA-256 (RT-02). O consumidor é o notificador de e-mail, no mesmo processo; o valor
 * nunca é serializado nem registrado em log (CP-11).
 */
public final class AuthEvents {

    private AuthEvents() {}

    /** Cadastro concluído. Consumido pelo envio do e-mail de verificação (TX-06). */
    public record UserRegisteredEvent(
            UUID userId, UUID tenantId, String email, String fullName, String rawVerificationToken)
            implements DomainEvent {}

    /** Reenvio de verificação (RN-457). */
    public record VerificationResentEvent(
            UUID userId, String email, String fullName, String rawVerificationToken)
            implements DomainEvent {}

    /** RN-461: solicitação de redefinição de senha. */
    public record PasswordResetRequestedEvent(
            UUID userId, String email, String fullName, String rawResetToken)
            implements DomainEvent {}

    /** RN-454: senha alterada ou redefinida; o titular é avisado para reagir a uma troca alheia. */
    public record PasswordChangedEvent(
            UUID userId, String email, String fullName, Instant changedAt) implements DomainEvent {}

    /** AU-06 / RN-453: conta bloqueada por tentativas de acesso. */
    public record AccountLockedEvent(
            UUID userId, String email, String fullName, Instant lockedUntil)
            implements DomainEvent {}

    /**
     * RN-005 / RT-04: reuso de refresh token detectado.
     *
     * <p>Consumido por auditoria e métrica com severidade crítica (§29). Não dispara e-mail: o
     * evento pode ocorrer legitimamente por corrida entre abas (CE-AU-02), e um alerta por e-mail a
     * cada ocorrência treinaria o titular a ignorá-lo.
     */
    public record RefreshTokenReuseDetectedEvent(UUID userId, int revokedCount)
            implements DomainEvent {}

    /** Telemetria de seleção de organização (§15). */
    public record TenantSelectedEvent(UUID userId, UUID tenantId) implements DomainEvent {}
}
