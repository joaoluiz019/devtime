package com.devtime.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Requests da feature 001 (spec §23, {@code authentication.md} §5).
 *
 * <p>BR-100: todos são {@code record} imutáveis. As anotações cobrem a camada 1 de validação
 * (formato, {@code 400}); a camada 2 — política de senha, unicidade, estado — fica no serviço,
 * porque depende de consulta e de regra de negócio (spec §17).
 */
public final class AuthRequests {

    private AuthRequests() {}

    /** Fuso padrão quando o cliente não informa (entities.md §6.1). */
    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    /** {@code POST /auth/register} (§5.2). */
    @Schema(description = "Criação de conta com organização própria")
    public record RegisterRequest(
            @NotBlank(message = "Informe um e-mail válido")
                    @Email(message = "Informe um e-mail válido")
                    @Size(max = 255)
                    String email,
            // O tamanho máximo também é verificado por RN-451; aqui ele existe para recusar
            // payloads absurdos antes de chegarem ao BCrypt, cujo custo é proposital.
            @NotBlank(message = "Informe a senha") @Size(min = 10, max = 128) String password,
            @NotBlank(message = "Informe seu nome completo") @Size(min = 2, max = 150)
                    String fullName,
            @Size(min = 2, max = 120) String tenantName,
            String timezone,
            @NotNull @AssertTrue(message = "É necessário aceitar os termos")
                    Boolean acceptedTerms) {

        /** entities.md §6.1: o nome da organização tem como padrão o nome do titular. */
        public String resolvedTenantName() {
            return tenantName == null || tenantName.isBlank() ? fullName : tenantName;
        }

        public String resolvedTimezone() {
            return timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
        }

        /**
         * INV-TEN-03 / CX-11: o fuso precisa ser um ID IANA resolvível.
         *
         * <p>Verificado contra {@link ZoneId#getAvailableZoneIds()}, e não por expressão regular: a
         * regex aceitaria {@code America/Atlantida}, que passaria na validação e falharia depois,
         * no primeiro cálculo de data do tenant.
         */
        @AssertTrue(message = "Fuso horário inválido")
        public boolean isTimezoneValid() {
            return timezone == null
                    || timezone.isBlank()
                    || ZoneId.getAvailableZoneIds().contains(timezone);
        }
    }

    /** {@code POST /auth/login} (§5.3). */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email, @NotBlank String password) {}

    /** {@code POST /auth/verify-email} (§5.6). */
    public record VerifyEmailRequest(@NotBlank(message = "Link inválido") String token) {}

    /** {@code POST /auth/resend-verification} (§5.1). */
    public record ResendVerificationRequest(@NotBlank @Email @Size(max = 255) String email) {}

    /** {@code POST /auth/select-tenant} (§5.5). */
    public record SelectTenantRequest(@NotNull(message = "Organização inválida") UUID tenantId) {}

    /** {@code POST /auth/forgot-password} (§5.7). */
    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}

    /** {@code POST /auth/reset-password} (§5.8). */
    public record ResetPasswordRequest(
            @NotBlank(message = "Link inválido") String token,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    /** {@code POST /auth/change-password} (§5.9). */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    /**
     * {@code POST /auth/invitations/{token}/accept} (§5.12).
     *
     * <p>Ambos os campos são condicionais: obrigatórios apenas quando o convite é para um endereço
     * que ainda não possui conta. A obrigatoriedade não pode ser declarada aqui porque depende de
     * uma consulta — fica no serviço, junto com a regra que a origina.
     */
    public record AcceptInvitationRequest(
            @Size(min = 2, max = 150) String fullName,
            @Size(min = 10, max = 128) String password) {}
}
