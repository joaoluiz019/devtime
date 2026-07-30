package com.devtime.shared.security;

import com.devtime.shared.config.DevTimeProperties;
import com.devtime.shared.persistence.UuidGenerator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emissão e validação do access token (security.md §5.2).
 *
 * <p>TK-01: assinatura {@code HS256} com segredo de no mínimo 256 bits. A migração para {@code
 * RS256} está prevista para F8, quando existir API pública com múltiplos verificadores.
 *
 * <p>A validação usa exclusivamente {@code Jwts.parser().verifyWith(key)}, que só aceita tokens
 * assinados com a chave configurada. Isso rejeita por construção o ataque {@code alg=none} e a
 * troca de algoritmo: um token sem assinatura ou assinado com outro algoritmo não satisfaz a
 * verificação e é recusado antes de qualquer claim ser lida.
 */
@Service
public class JwtService {

    static final String CLAIM_TENANT_ID = "tid";
    static final String CLAIM_MEMBERSHIP_ID = "mid";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TIMEZONE = "tz";

    private final SecretKey signingKey;
    private final DevTimeProperties properties;
    private final Clock clock;

    public JwtService(DevTimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = buildSigningKey(properties.security().jwtSecret());
    }

    /**
     * Emite um access token para uma sessão com tenant selecionado.
     *
     * @throws IllegalArgumentException se {@code tenantId}, {@code membershipId} ou {@code role}
     *     estiverem ausentes — para esse caso existe {@link #issuePreAuthToken(UUID)}
     */
    public String issueAccessToken(
            UUID userId, UUID tenantId, UUID membershipId, Role role, String timezone) {
        if (tenantId == null || membershipId == null || role == null) {
            throw new IllegalArgumentException(
                    "Access token com tenant exige tenantId, membershipId e role");
        }
        return builder(userId)
                .claim(CLAIM_TENANT_ID, tenantId.toString())
                .claim(CLAIM_MEMBERSHIP_ID, membershipId.toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TIMEZONE, timezone)
                .compact();
    }

    /**
     * Emite o token de pré-seleção, sem a claim {@code tid}.
     *
     * <p>Usado quando o usuário pertence a mais de um tenant (security.md §5.1). CE-P-11 restringe
     * o que ele pode acessar aos endpoints de seleção de tenant.
     */
    public String issuePreAuthToken(UUID userId) {
        return builder(userId).compact();
    }

    /**
     * Valida assinatura, emissor, público-alvo e expiração, devolvendo as claims tipadas.
     *
     * @throws InvalidAccessTokenException para qualquer falha de validação, sem distinguir a causa
     *     na mensagem exposta ao cliente (security.md §14)
     */
    public AccessTokenClaims parse(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .requireIssuer(properties.api().issuer())
                            .requireAudience(properties.api().audience())
                            // CE-S-08: tolerância para relógios dessincronizados.
                            .clockSkewSeconds(properties.security().clockSkew().toSeconds())
                            .clock(() -> Date.from(clock.instant()))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return toAccessTokenClaims(claims);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidAccessTokenException("Access token inválido", e);
        }
    }

    private io.jsonwebtoken.JwtBuilder builder(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Access token exige userId");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.security().accessTokenTtl());
        return Jwts.builder()
                .issuer(properties.api().issuer())
                .audience()
                .add(properties.api().audience())
                .and()
                .subject(userId.toString())
                .id(UuidGenerator.newId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256);
    }

    private AccessTokenClaims toAccessTokenClaims(Claims claims) {
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                readUuid(claims, CLAIM_TENANT_ID),
                readUuid(claims, CLAIM_MEMBERSHIP_ID),
                readRole(claims),
                claims.get(CLAIM_TIMEZONE, String.class),
                UUID.fromString(claims.getId()),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant());
    }

    private UUID readUuid(Claims claims, String name) {
        String raw = claims.get(name, String.class);
        return raw == null ? null : UUID.fromString(raw);
    }

    private Role readRole(Claims claims) {
        String raw = claims.get(CLAIM_ROLE, String.class);
        if (raw == null) {
            return null;
        }
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // Papel desconhecido é token inválido, não papel sem permissão: aceitar e cair no
            // conjunto vazio esconderia um token forjado ou uma migração de enum mal feita.
            throw new InvalidAccessTokenException("Papel desconhecido na claim role", e);
        }
    }

    /**
     * Deriva a chave de assinatura dos bytes UTF-8 do segredo.
     *
     * <p>TK-01 exige 256 bits, ou seja, no mínimo 32 caracteres — o mesmo mínimo declarado em
     * {@code DevTimeProperties.SecurityProps}. {@code Keys.hmacShaKeyFor} recusa chaves menores,
     * transformando um segredo fraco em falha de inicialização (CF-03) em vez de assinatura frágil
     * em produção.
     *
     * <p>Deliberadamente <b>não</b> tenta decodificar Base64 antes: decodificadores Base64 são
     * tolerantes e descartam caracteres inválidos em silêncio, então um segredo forte em texto puro
     * que contenha {@code -} ou {@code !} produziria uma chave muito mais curta que o esperado —
     * uma fraqueza criptográfica invisível na configuração. Um único formato torna o comprimento do
     * segredo verificável por inspeção.
     */
    private static SecretKey buildSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
