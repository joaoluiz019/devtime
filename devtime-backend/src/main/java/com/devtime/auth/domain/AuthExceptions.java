package com.devtime.auth.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exceções de regra da feature 001 (spec §27).
 *
 * <p>BR-063: cada instância vem de um método fábrica nomeado pela regra que a origina. Reunir as
 * fábricas em um ponto único é o que torna verificável, por leitura, que todo código da §8 de
 * {@code authentication.md} tem representação em código — e que nenhum deles carrega detalhe capaz
 * de distinguir contas (SG-01 a SG-03).
 */
public final class AuthExceptions {

    private AuthExceptions() {}

    /**
     * AU-01 / SG-03: credenciais inválidas.
     *
     * <p>Resposta idêntica para e-mail inexistente e senha incorreta, sem nenhum detalhe adicional.
     * Qualquer campo extra aqui — inclusive o e-mail informado — reabriria o canal de enumeração
     * que AU-01 fecha.
     */
    public static BusinessRuleException invalidCredentials() {
        return new InvalidCredentialsException();
    }

    /** INV-USR-04: o usuário autenticou, mas não possui membership ativo em nenhum tenant. */
    public static BusinessRuleException noActiveMembership() {
        return new NoActiveMembershipException();
    }

    /** §4.2 de state-machines.md: conta em {@code PENDING_ACTIVATION}. */
    public static BusinessRuleException emailNotVerified() {
        return new EmailNotVerifiedException();
    }

    /** RN-453: bloqueio temporário após 5 falhas em 15 minutos. */
    public static BusinessRuleException accountLocked(Instant lockedUntil) {
        return new AccountLockedException(lockedUntil);
    }

    /** Cookie de refresh ausente, desconhecido, revogado ou expirado (CX-06). */
    public static BusinessRuleException refreshTokenInvalid() {
        return new RefreshTokenInvalidException();
    }

    /** RN-005 / RT-04: token já rotacionado reapresentado — toda a cadeia foi revogada. */
    public static BusinessRuleException refreshTokenReuseDetected() {
        return new RefreshTokenReuseDetectedException();
    }

    /** RN-461: token de redefinição expirado ou já consumido. */
    public static BusinessRuleException passwordResetTokenInvalid() {
        return new PasswordResetTokenInvalidException();
    }

    /** §4.2 de state-machines.md: token de verificação emitido há mais de 7 dias. */
    public static BusinessRuleException verificationTokenExpired() {
        return new VerificationTokenExpiredException();
    }

    /** Token de verificação desconhecido. {@code 404}, distinto de expirado ({@code 410}). */
    public static BusinessRuleException verificationTokenInvalid() {
        return new VerificationTokenInvalidException();
    }

    /** PW-05: a alteração de senha exige a senha atual correta. */
    public static BusinessRuleException currentPasswordIncorrect() {
        return new CurrentPasswordIncorrectException();
    }

    /** {@code authentication.md} §5.9: a nova senha precisa ser diferente da atual. */
    public static BusinessRuleException passwordUnchanged() {
        return new PasswordUnchangedException();
    }

    /** RN-451: senha fora da política. Os requisitos violados viajam; a senha, nunca (PW-08). */
    public static BusinessRuleException passwordPolicyViolated(Set<String> violations) {
        return new PasswordPolicyViolationException(violations);
    }

    /** RN-452: e-mail já pertence a um usuário não excluído. */
    public static BusinessRuleException emailAlreadyRegistered() {
        return new EmailAlreadyRegisteredException();
    }

    /** RN-457: convite emitido há mais de 7 dias ou invalidado por reenvio. */
    public static BusinessRuleException invitationExpired() {
        return new InvitationExpiredException();
    }

    /** Convite desconhecido ou revogado. */
    public static BusinessRuleException invitationInvalid() {
        return new InvitationInvalidException();
    }

    /** {@code authentication.md} §5.12: o usuário já é membro deste tenant. */
    public static BusinessRuleException alreadyMember() {
        return new AlreadyMemberException();
    }

    /**
     * §5.12: o aceite exige senha quando o convidado não está autenticado.
     *
     * <p>{@code 400} e não {@code 422}: é ausência de campo obrigatório em um caso condicional, não
     * regra de negócio violada. A condicionalidade impede declará-la por Bean Validation, mas não
     * muda a natureza do erro.
     */
    public static BusinessRuleException invitationPasswordRequired() {
        return new InvitationPasswordRequiredException();
    }

    /** RN-459: membership {@code INVITED}, {@code SUSPENDED} ou {@code REMOVED}. */
    public static BusinessRuleException membershipInactive() {
        return new MembershipInactiveException();
    }

    /** RN-007: tenant suspenso aceita apenas leitura. */
    public static BusinessRuleException tenantSuspended() {
        return new TenantSuspendedException();
    }

    /** RN-008: tenant cancelado rejeita qualquer acesso. */
    public static BusinessRuleException tenantCancelled() {
        return new TenantCancelledException();
    }

    /** AU-01 / SG-03. */
    public static final class InvalidCredentialsException extends BusinessRuleException {
        private InvalidCredentialsException() {
            super(ErrorCode.AUTHENTICATION_REQUIRED, Map.of(), "Credenciais inválidas");
        }
    }

    /** INV-USR-04. */
    public static final class NoActiveMembershipException extends BusinessRuleException {
        private NoActiveMembershipException() {
            super(ErrorCode.NO_ACTIVE_MEMBERSHIP, Map.of(), "Usuário sem organização ativa");
        }
    }

    /** §4.2 de state-machines.md. */
    public static final class EmailNotVerifiedException extends BusinessRuleException {
        private EmailNotVerifiedException() {
            super(
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    // FA-02: a UI precisa saber que existe ação disponível; o campo não revela a
                    // existência da conta, porque só é alcançado após a senha correta.
                    Map.of("canResendVerification", true),
                    "E-mail não verificado");
        }
    }

    /** RN-453. */
    public static final class AccountLockedException extends BusinessRuleException {
        private AccountLockedException(Instant lockedUntil) {
            super(
                    ErrorCode.ACCOUNT_LOCKED,
                    lockedUntil == null ? Map.of() : Map.of("lockedUntil", lockedUntil.toString()),
                    "Conta temporariamente bloqueada");
        }
    }

    /** CX-06. */
    public static final class RefreshTokenInvalidException extends BusinessRuleException {
        private RefreshTokenInvalidException() {
            super(ErrorCode.REFRESH_TOKEN_INVALID, Map.of(), "Refresh token inválido ou expirado");
        }
    }

    /** RN-005 / RT-04. */
    public static final class RefreshTokenReuseDetectedException extends BusinessRuleException {
        private RefreshTokenReuseDetectedException() {
            super(
                    ErrorCode.REFRESH_TOKEN_REUSE_DETECTED,
                    Map.of(),
                    "Reuso de refresh token detectado");
        }
    }

    /** RN-461. */
    public static final class PasswordResetTokenInvalidException extends BusinessRuleException {
        private PasswordResetTokenInvalidException() {
            super(
                    ErrorCode.PASSWORD_RESET_TOKEN_INVALID,
                    Map.of(),
                    "Token de redefinição expirado ou já utilizado");
        }
    }

    /** §4.2 de state-machines.md. */
    public static final class VerificationTokenExpiredException extends BusinessRuleException {
        private VerificationTokenExpiredException() {
            super(
                    ErrorCode.VERIFICATION_TOKEN_EXPIRED,
                    Map.of("canResendVerification", true),
                    "Token de verificação expirado");
        }
    }

    /** {@code authentication.md} §5.6. */
    public static final class VerificationTokenInvalidException extends BusinessRuleException {
        private VerificationTokenInvalidException() {
            super(ErrorCode.VERIFICATION_TOKEN_INVALID, Map.of(), "Token de verificação inválido");
        }
    }

    /** PW-05. */
    public static final class CurrentPasswordIncorrectException extends BusinessRuleException {
        private CurrentPasswordIncorrectException() {
            super(
                    ErrorCode.CURRENT_PASSWORD_INCORRECT,
                    Map.of("field", "currentPassword"),
                    "Senha atual incorreta");
        }
    }

    /** {@code authentication.md} §5.9. */
    public static final class PasswordUnchangedException extends BusinessRuleException {
        private PasswordUnchangedException() {
            super(
                    ErrorCode.PASSWORD_UNCHANGED,
                    Map.of("field", "newPassword"),
                    "A nova senha deve ser diferente da atual");
        }
    }

    /** RN-451. */
    public static final class PasswordPolicyViolationException extends BusinessRuleException {
        private PasswordPolicyViolationException(Set<String> violations) {
            super(
                    ErrorCode.PASSWORD_POLICY_VIOLATION,
                    // TreeSet: a ordem estável torna a resposta determinística e o teste legível.
                    Map.of("field", "password", "requirements", new TreeSet<>(violations)),
                    "Senha não atende à política");
        }
    }

    /** RN-452. */
    public static final class EmailAlreadyRegisteredException extends BusinessRuleException {
        private EmailAlreadyRegisteredException() {
            super(
                    ErrorCode.EMAIL_ALREADY_REGISTERED,
                    // SG-01 / AC-001-12: nenhum dado do usuário existente é exposto — nem status,
                    // nem nome, nem data de cadastro.
                    Map.of("field", "email"),
                    "E-mail já cadastrado");
        }
    }

    /** RN-457. */
    public static final class InvitationExpiredException extends BusinessRuleException {
        private InvitationExpiredException() {
            super(ErrorCode.INVITATION_EXPIRED, Map.of(), "Convite expirado");
        }
    }

    /** {@code authentication.md} §5.12. */
    public static final class InvitationInvalidException extends BusinessRuleException {
        private InvitationInvalidException() {
            super(ErrorCode.INVITATION_INVALID, Map.of(), "Convite inválido ou revogado");
        }
    }

    /** {@code authentication.md} §5.12. */
    public static final class AlreadyMemberException extends BusinessRuleException {
        private AlreadyMemberException() {
            super(ErrorCode.ALREADY_MEMBER, Map.of(), "Usuário já é membro desta organização");
        }
    }

    /** {@code authentication.md} §5.12. */
    public static final class InvitationPasswordRequiredException extends BusinessRuleException {
        private InvitationPasswordRequiredException() {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "password"),
                    "Senha obrigatória para aceitar o convite");
        }
    }

    /** RN-459. */
    public static final class MembershipInactiveException extends BusinessRuleException {
        private MembershipInactiveException() {
            super(ErrorCode.MEMBERSHIP_INACTIVE, Map.of(), "Membership inativo");
        }
    }

    /** RN-007. */
    public static final class TenantSuspendedException extends BusinessRuleException {
        private TenantSuspendedException() {
            super(ErrorCode.TENANT_SUSPENDED, Map.of(), "Organização suspensa: apenas leitura");
        }
    }

    /** RN-008. */
    public static final class TenantCancelledException extends BusinessRuleException {
        private TenantCancelledException() {
            super(ErrorCode.TENANT_CANCELLED, Map.of(), "Organização cancelada");
        }
    }
}
