package com.baz.searchapi.repository;

import com.baz.searchapi.model.entity.Client;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Case-insensitive substring search across all client text fields.
     * Matches anywhere in first_name, last_name, email, or description —
     * e.g. "Outlook" matches "john.doe@outlook.com".
     */
    @Query(value = """
        SELECT * FROM clients
        WHERE first_name  ILIKE '%' || :query || '%'
           OR last_name   ILIKE '%' || :query || '%'
           OR email       ILIKE '%' || :query || '%'
           OR description ILIKE '%' || :query || '%'
        """, nativeQuery = true)
    List<Client> fullTextSearch(@Param("query") String query);
}
