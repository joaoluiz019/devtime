package com.devtime.client.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/** Pessoa de contato dentro de um cliente (entities.md §6.5). */
@Entity
@Table(name = "contacts")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Contact extends TenantScopedEntity {

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Cargo ou função no cliente.
     *
     * <p>A coluna é {@code contact_role} e não {@code role} para não colidir com o papel de
     * autorização ({@code memberships.role}) em consultas e em leitura de log — são conceitos
     * distintos e a homonímia já causou confusão em revisão.
     */
    @Column(name = "contact_role", length = 80)
    private String role;

    /** INV-CON-01 / RN-406: no máximo um por cliente; marcar um novo desmarca o anterior. */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    /** Destinatário padrão no envio de relatórios (consumido por {@code 012-reports}). */
    @Column(name = "receives_reports", nullable = false)
    private boolean receivesReports;
}
