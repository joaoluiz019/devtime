package com.devtime.auth.domain;

import com.devtime.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Token de uso único para verificação de e-mail, redefinição de senha e convite (spec 001 §13.1).
 *
 * <p>Não estende {@code TenantScopedEntity}: os fluxos de verificação e de redefinição ocorrem
 * <b>antes</b> de existir tenant selecionado, e o convite é consumido por alguém que ainda não é
 * membro. Aplicar o filtro automático faria o token desaparecer exatamente no momento em que
 * precisa ser encontrado. O acesso é sempre pelo hash — valor imprevisível de 256 bits — o que
 * torna a enumeração inviável mesmo sem recorte por tenant.
 *
 * <p>RT-02 aplicado por analogia: apenas o SHA-256 é persistido. O valor bruto existe uma única
 * vez, no link enviado por e-mail.
 */
@Entity
@Table(name = "verification_tokens")
@SQLRestriction("deleted_at IS NULL") // BR-029
@Getter
@Setter
public class VerificationToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Preenchido apenas em {@link VerificationTokenType#INVITATION}. */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private VerificationTokenType type;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** RN-461: preenchido na mesma transação do efeito, garantindo uso único. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** RN-457: preenchido quando um reenvio substitui este token. */
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isInvalidated() {
        return invalidatedAt != null;
    }

    /**
     * O token não serve mais para nada — usado, substituído ou vencido.
     *
     * <p>As três causas produzem a mesma resposta ao cliente: distinguir revelaria se alguém já
     * usou o link, informação de valor apenas para quem o interceptou.
     */
    public boolean isSettledAt(Instant reference) {
        return isConsumed() || isInvalidated() || isExpiredAt(reference);
    }

    public boolean isExpiredAt(Instant reference) {
        return !reference.isBefore(expiresAt);
    }
}
