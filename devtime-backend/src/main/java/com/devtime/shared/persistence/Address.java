package com.devtime.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Value Object de endereço, achatado nas colunas {@code address_*} (entities.md §7.1).
 *
 * <p>Fica em {@code shared} porque é compartilhado por {@code Tenant} e {@code Client} sem ser
 * regra de negócio de nenhum dos dois (CE-B-07).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Column(name = "address_street", length = 200)
    private String street;

    @Column(name = "address_number", length = 20)
    private String number;

    @Column(name = "address_complement", length = 100)
    private String complement;

    @Column(name = "address_district", length = 100)
    private String district;

    @Column(name = "address_city", length = 100)
    private String city;

    @Column(name = "address_state", length = 50)
    private String state;

    @Column(name = "address_zip_code", length = 20)
    private String zipCode;

    /** ISO-3166-1 alfa-2. {@code CHAR(2)} no schema, daí a declaração explícita do tipo fixo. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "address_country", length = 2)
    private String country;
}
