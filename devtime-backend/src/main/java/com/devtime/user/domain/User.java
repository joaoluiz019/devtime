package com.devtime.user.domain;

import com.devtime.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Identidade autenticável (entities.md §6.2).
 *
 * <p>Não estende {@code TenantScopedEntity}: ART-013 exclui {@code User} do escopo de tenant,
 * porque um usuário pode pertencer a vários tenants — o vínculo com papel é {@code Membership}.
 *
 * <p>INV-USR-02: {@code passwordHash} <b>nunca</b> é retornado por nenhum endpoint. A garantia vem
 * da proibição de expor entidade JPA (ART-061) somada ao teste de contrato que verifica a ausência
 * do campo nas respostas (BR-108).
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL") // BR-029
@Getter
@Setter
public class User extends BaseEntity {

    /** INV-USR-01: único entre não excluídos. AU-03: armazenado em minúsculas. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** ART-081: BCrypt custo 12. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** RN-453: bloqueio após 5 falhas em 15 minutos. Zerado em login bem-sucedido. */
    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** TK-04: access tokens emitidos antes deste instante são rejeitados. */
    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    /** Preferência pessoal; herda do tenant quando nula. */
    @Column(name = "timezone", length = 60)
    private String timezone;

    @Column(name = "locale", length = 10)
    private String locale;

    /** Preferências de interface (entities.md §6.2.1). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", nullable = false)
    private String preferences;
}
