package com.baz.searchapi.repository;

import com.baz.searchapi.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByClientIdAndTitleIgnoreCase(UUID clientId, String title);
}
