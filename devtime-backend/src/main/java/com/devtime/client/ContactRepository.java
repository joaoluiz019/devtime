package com.devtime.client;

import com.devtime.client.domain.Contact;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistência de {@link Contact} (spec 003 §25). */
@Repository
public interface ContactRepository extends SoftDeleteRepository<Contact> {

    @Query("SELECT c FROM Contact c WHERE c.clientId = :clientId ORDER BY c.name ASC")
    List<Contact> findByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT COUNT(c) FROM Contact c WHERE c.clientId = :clientId")
    long countByClientId(@Param("clientId") UUID clientId);

    /** RN-406: o principal atual, que será desmarcado ao promover outro (CX-08). */
    @Query("SELECT c FROM Contact c WHERE c.clientId = :clientId AND c.primary = true")
    Optional<Contact> findPrimaryByClientId(@Param("clientId") UUID clientId);
}
