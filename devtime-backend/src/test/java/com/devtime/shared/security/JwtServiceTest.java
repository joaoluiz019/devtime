package com.devtime.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.config.DevTimeProperties;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Emissão e validação do access token (security.md §5.2).
 *
 * <p>Cobre os controles TK-01 a TK-06 e o requisito de rejeição explícita de {@code alg=none}
 * (T-001-11). Sem relógio real (BR-205): o instante é fixo, o que torna a asserção de expiração uma
 * igualdade exata.
 */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T14:32:10Z");
    private static final String ISSUER = "https://api.devtime.test";
    private static final String AUDIENCE = "devtime-web";
    private static final String SECRET = "segredo-de-teste-com-mais-de-256-bits-de-entropia!!";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(properties(SECRET), clock);

    private final UUID userId = UUID.fromString("0192f3a4-1234-7890-abcd-ef0123456789");
    private final UUID tenantId = UUID.fromString("0192f3a4-aaaa-7890-abcd-ef0123456789");
    private final UUID membershipId = UUID.fromString("0192f3a4-bbbb-7890-abcd-ef0123456789");

    @Test
    @DisplayName("TK-02: o access token expira em 15 minutos e preserva todas as claims da sessão")
    void shouldIssueAndParseAccessToken() {
        String token =
                jwtService.issueAccessToken(
                        userId, tenantId, membershipId, Role.OWNER, "America/Sao_Paulo");

        AccessTokenClaims claims = jwtService.parse(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.membershipId()).isEqualTo(membershipId);
        assertThat(claims.role()).isEqualTo(Role.OWNER);
        assertThat(claims.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(claims.tokenId()).isNotNull();
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(claims.hasTenantSelected()).isTrue();
    }

    @Test
    @DisplayName("TK-03: o token não carrega a lista de permissões")
    void tokenMustNotCarryPermissions() {
        String token =
                jwtService.issueAccessToken(
                        userId, tenantId, membershipId, Role.MEMBER, "America/Sao_Paulo");

        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                        StandardCharsets.UTF_8);

        assertThat(payload)
                .as(
                        "incluir permissões as congelaria por 15 minutos; um ADMIN rebaixado manteria"
                                + " privilégios até o token expirar")
                .doesNotContain("perms")
                .doesNotContain("permissions");
    }

    @Test
    @DisplayName("TK-06: o token não carrega e-mail, nome nem documento")
    void tokenMustNotCarrySensitiveData() {
        String token =
                jwtService.issueAccessToken(
                        userId, tenantId, membershipId, Role.OWNER, "America/Sao_Paulo");

        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                        StandardCharsets.UTF_8);

        assertThat(payload).doesNotContain("@").doesNotContain("email").doesNotContain("fullName");
    }

    @Test
    @DisplayName("security.md §3: o token de pré-seleção não possui as claims tid, mid e role")
    void preAuthTokenMustNotCarryTenant() {
        String token = jwtService.issuePreAuthToken(userId);

        AccessTokenClaims claims = jwtService.parse(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tenantId()).isNull();
        assertThat(claims.membershipId()).isNull();
        assertThat(claims.role()).isNull();
        assertThat(claims.hasTenantSelected()).isFalse();
    }

    @Test
    @DisplayName("T-001-11: token com alg=none é rejeitado")
    void unsignedTokenMustBeRejected() {
        String unsigned =
                Jwts.builder()
                        .issuer(ISSUER)
                        .audience()
                        .add(AUDIENCE)
                        .and()
                        .subject(userId.toString())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(Date.from(NOW))
                        .expiration(Date.from(NOW.plusSeconds(900)))
                        .compact();

        assertThatThrownBy(() -> jwtService.parse(unsigned))
                .as(
                        "um token sem assinatura não satisfaz verifyWith e é recusado antes de ler claims")
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("TK-01: token assinado com outro segredo é rejeitado")
    void tokenSignedWithAnotherSecretMustBeRejected() {
        var foreignKey =
                new SecretKeySpec(
                        "outro-segredo-com-mais-de-256-bits-de-entropia!!!"
                                .getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256");
        String forged =
                Jwts.builder()
                        .issuer(ISSUER)
                        .audience()
                        .add(AUDIENCE)
                        .and()
                        .subject(userId.toString())
                        .claim("tid", tenantId.toString())
                        .claim("role", Role.OWNER.name())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(Date.from(NOW))
                        .expiration(Date.from(NOW.plusSeconds(900)))
                        .signWith(foreignKey, Jwts.SIG.HS256)
                        .compact();

        assertThatThrownBy(() -> jwtService.parse(forged))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("TK-02: token expirado além da tolerância de relógio é rejeitado")
    void expiredTokenMustBeRejected() {
        JwtService pastIssuer =
                new JwtService(
                        properties(SECRET),
                        Clock.fixed(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
        String expired =
                pastIssuer.issueAccessToken(
                        userId, tenantId, membershipId, Role.OWNER, "America/Sao_Paulo");

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("CE-S-08: token emitido 20s no futuro é aceito pela tolerância de 30s")
    void tokenWithinClockSkewMustBeAccepted() {
        JwtService futureIssuer =
                new JwtService(
                        properties(SECRET), Clock.fixed(NOW.plusSeconds(20), ZoneOffset.UTC));
        String token =
                futureIssuer.issueAccessToken(
                        userId, tenantId, membershipId, Role.OWNER, "America/Sao_Paulo");

        assertThat(jwtService.parse(token).userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("security.md §5.2: token com emissor divergente é rejeitado")
    void tokenWithWrongIssuerMustBeRejected() {
        JwtService otherIssuer =
                new JwtService(
                        new DevTimeProperties(
                                new DevTimeProperties.ApiProps(
                                        "https://atacante.example", AUDIENCE),
                                new DevTimeProperties.CorsProps(List.of("http://localhost:4200")),
                                securityProps(SECRET),
                                appProps(),
                                mailProps()),
                        clock);
        String token =
                otherIssuer.issueAccessToken(
                        userId, tenantId, membershipId, Role.OWNER, "America/Sao_Paulo");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("security.md §5.2: token com público-alvo divergente é rejeitado")
    void tokenWithWrongAudienceMustBeRejected() {
        JwtService otherAudience =
                new JwtService(
                        new DevTimeProperties(
                                new DevTimeProperties.ApiProps(ISSUER, "outro-cliente"),
                                new DevTimeProperties.CorsProps(List.of("http://localhost:4200")),
                                securityProps(SECRET),
                                appProps(),
                                mailProps()),
                        clock);
        String token =
                otherAudience.issueAccessToken(
                        userId, tenantId, membershipId, Role.OWNER, "America/Sao_Paulo");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("Papel desconhecido na claim role é tratado como token inválido")
    void unknownRoleMustBeRejected() {
        String token =
                Jwts.builder()
                        .issuer(ISSUER)
                        .audience()
                        .add(AUDIENCE)
                        .and()
                        .subject(userId.toString())
                        .claim("tid", tenantId.toString())
                        .claim("role", "SUPER_ADMIN")
                        .id(UUID.randomUUID().toString())
                        .issuedAt(Date.from(NOW))
                        .expiration(Date.from(NOW.plusSeconds(900)))
                        .signWith(
                                new SecretKeySpec(
                                        SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"),
                                Jwts.SIG.HS256)
                        .compact();

        assertThatThrownBy(() -> jwtService.parse(token))
                .as("aceitar e cair no conjunto vazio de permissões esconderia um token forjado")
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("TK-01: segredo abaixo de 256 bits impede a inicialização")
    void weakSecretMustFailFast() {
        assertThatThrownBy(() -> new JwtService(properties("curto"), clock))
                .as(
                        "CF-03: segredo fraco vira falha de inicialização, não assinatura frágil em produção")
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    @Test
    @DisplayName("Emitir token com tenant exige tenantId, membershipId e role")
    void accessTokenRequiresFullSession() {
        assertThatThrownBy(
                        () ->
                                jwtService.issueAccessToken(
                                        userId,
                                        null,
                                        membershipId,
                                        Role.OWNER,
                                        "America/Sao_Paulo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DevTimeProperties properties(String secret) {
        return new DevTimeProperties(
                new DevTimeProperties.ApiProps(ISSUER, AUDIENCE),
                new DevTimeProperties.CorsProps(List.of("http://localhost:4200")),
                securityProps(secret),
                appProps(),
                mailProps());
    }

    private static DevTimeProperties.AppProps appProps() {
        return new DevTimeProperties.AppProps("http://localhost:4200");
    }

    private static DevTimeProperties.MailProps mailProps() {
        return new DevTimeProperties.MailProps("nao-responda@devtime.test");
    }

    private static DevTimeProperties.SecurityProps securityProps(String secret) {
        return new DevTimeProperties.SecurityProps(
                secret, Duration.ofMinutes(15), Duration.ofDays(30), 4, Duration.ofSeconds(30));
    }
}
