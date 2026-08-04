package com.devtime.auth.dto;

import com.devtime.shared.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Responses da feature 001 (spec §23, {@code authentication.md} §5).
 *
 * <p>CP-01 / INV-USR-02 / BR-108: nenhum destes tipos possui campo de senha, hash ou valor bruto de
 * token. O refresh token trafega exclusivamente em cookie {@code HttpOnly} (CA-02 de {@code
 * authentication.md}), nunca no corpo — por isso não existe campo para ele em {@link
 * SessionResponse}.
 */
public final class AuthResponses {

    private AuthResponses() {}

    /** {@code POST /auth/register} → {@code 201}. Sem token: o login exige verificação (CP-08). */
    @Schema(description = "Conta e organização criadas")
    public record RegisterResponse(
            UUID userId,
            UUID tenantId,
            String email,
            String status,
            boolean verificationEmailSent) {}

    /** Usuário autenticado, como aparece na resposta de sessão. */
    public record AuthenticatedUser(
            UUID id, String fullName, String displayName, String email, String avatarUrl) {}

    /** Organização da sessão corrente. */
    public record AuthenticatedTenant(
            UUID id, String name, String slug, String timezone, String currency, String logoUrl) {}

    /**
     * Opção de organização na seleção (§5.3, múltiplos tenants).
     *
     * <p>CX-08: organizações suspensas aparecem marcadas por {@code status}, não omitidas.
     */
    public record TenantOptionResponse(
            UUID id, String name, String slug, Role role, String logoUrl, String status) {}

    /**
     * Resposta de {@code login}, {@code refresh}, {@code select-tenant} e {@code verify-email}.
     *
     * <p>Um único tipo para os quatro porque {@code authentication.md} §5.4, §5.5 e §5.6 dizem
     * literalmente "mesma estrutura do login": tipos distintos com os mesmos campos obrigariam o
     * cliente a escrever quatro desserializações equivalentes.
     *
     * @param tenantSelectionRequired verdadeiro quando o token é de pré-seleção (sem {@code tid})
     * @param permissions derivadas do papel a cada emissão (TK-03); não viajam no token
     */
    public record SessionResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            boolean tenantSelectionRequired,
            AuthenticatedUser user,
            AuthenticatedTenant tenant,
            Role role,
            List<String> permissions,
            List<TenantOptionResponse> tenants) {

        public static final String BEARER = "Bearer";
    }

    /** Resposta genérica de operação assíncrona ou sem conteúdo próprio (§5.7, §5.8). */
    public record MessageResponse(String message) {}

    /** Usuário completo de {@code GET /auth/me} (§5.10). */
    public record MeUser(
            UUID id,
            String email,
            String fullName,
            String displayName,
            String avatarUrl,
            String timezone,
            String locale,
            Map<String, Object> preferences) {}

    /** Organização completa de {@code GET /auth/me} (§5.10). */
    public record MeTenant(
            UUID id,
            String name,
            String slug,
            String timezone,
            String currency,
            String locale,
            String logoUrl,
            String status,
            String planCode,
            Map<String, Object> settings) {}

    /** Vínculo da sessão corrente. */
    public record MeMembership(UUID id, Role role, String status) {}

    /**
     * Cronômetro em andamento do usuário autenticado (§5.10).
     *
     * <p>Projeção estreita de {@code TimerResponse}, e não o DTO inteiro: {@code /auth/me} responde
     * "o que a barra do cronômetro precisa desenhar agora", não "tudo sobre este cronômetro". Quem
     * precisa do restante chama {@code GET /timers/current}, que é a fonte da feature 009.
     *
     * @param ticketKey chave legível ({@code CT-0001-42}); é o que a barra exibe
     */
    public record MeActiveTimer(
            UUID id,
            String status,
            String ticketKey,
            java.time.Instant startedAt,
            int accumulatedActiveSeconds) {}

    /**
     * {@code GET /auth/me} (§5.10).
     *
     * <p>{@code activeTimer} é nulo — e, por §4.1, omitido do JSON — quando não há cronômetro em
     * andamento. Ele vem daqui e não de uma segunda requisição por decisão explícita de §5.10: ao
     * carregar a aplicação, uma chamada só recupera sessão, permissões e cronômetro, eliminando o
     * intervalo em que a barra apareceria vazia.
     *
     * <p>RN-150 torna o cronômetro único por <b>pessoa</b>, entre organizações. O que {@code
     * /auth/me} devolve é o cronômetro da pessoa, mesmo que ele esteja rodando em outra organização
     * — diferente do painel, que por CX-11 de {@code specs/010} só conta o do tenant corrente. As
     * duas leituras são deliberadamente distintas: a barra existe para que ninguém esqueça um
     * cronômetro rodando, e escondê-lo ao trocar de organização produziria exatamente esse
     * esquecimento.
     */
    public record MeResponse(
            MeUser user,
            MeTenant tenant,
            MeMembership membership,
            List<String> permissions,
            List<TenantOptionResponse> availableTenants,
            MeActiveTimer activeTimer) {}

    /**
     * Sessão ativa (§5.11).
     *
     * @param ipAddress mascarado parcialmente (§9.2 de security.md)
     */
    public record ActiveSessionResponse(
            UUID id,
            boolean current,
            String userAgent,
            String ipAddress,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt) {}

    /** Envelope de {@code GET /auth/sessions} (§5.11). */
    public record ActiveSessionListResponse(List<ActiveSessionResponse> content) {}

    /**
     * Convite consultado antes do aceite (§5.12).
     *
     * <p>Expõe apenas o necessário para a tela de aceite. Nenhum dado interno da organização — nem
     * quantidade de membros, nem plano, nem identificador de quem convidou — atravessa este
     * contrato: quem o consulta ainda não é membro.
     */
    public record InvitationResponse(
            String tenantName,
            String tenantLogoUrl,
            String invitedByName,
            Role role,
            String email,
            boolean userExists,
            Instant expiresAt) {}
}
