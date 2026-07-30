package com.devtime.client.domain;

import com.devtime.shared.persistence.Address;
import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Cliente do tenant (entities.md §6.4).
 *
 * <p>É a contraparte de todo contrato e o destinatário de todo relatório. Sem razão social,
 * documento e contato corretos, o PDF entregue não é apresentável (PV-05).
 *
 * <p>Os contatos <b>não</b> são mapeados como {@code @OneToMany}: BR-032 prefere consulta explícita
 * por identificador, o que evita carregar a coleção inteira em toda leitura de cliente — a
 * listagem, que é a tela mais acessada, não precisa deles.
 */
@Entity
@Table(name = "clients")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Client extends TenantScopedEntity {

    /** RN-404: único por tenant sem diferenciar caixa; acentos são diferenciados (CE-C-02). */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Razão social usada no cabeçalho dos relatórios. */
    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 10)
    private DocumentType documentType;

    /** RN-402/RN-403: apenas dígitos, validado quando CPF ou CNPJ, único por tenant. */
    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "website", length = 255)
    private String website;

    @Embedded private Address address;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** Derivada do nome quando ausente, para identificação visual estável em gráficos. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ClientStatus status;

    /**
     * Contratos em {@code ACTIVE} ou {@code SUSPENDED} (campo desnormalizado, entities.md §9).
     *
     * <p>É a fonte de RN-401 e de RN-407. A contagem inclui {@code SUSPENDED} porque é exatamente
     * esse o conjunto que RN-401 protege contra exclusão; contratos {@code ENDED} e {@code
     * CANCELLED} não impedem excluir o cliente (CE-C-06).
     *
     * <p>Mantida incrementalmente pelas transições de {@code 004-contracts}, com reconciliação
     * noturna por agregação real — a desnormalização só é aceitável acompanhada dela (§9).
     */
    @Column(name = "active_contracts_count", nullable = false)
    private int activeContractsCount;
}
