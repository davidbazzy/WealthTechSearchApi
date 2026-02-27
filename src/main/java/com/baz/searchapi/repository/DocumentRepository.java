package com.baz.searchapi.repository;

import com.baz.searchapi.model.entity.Document;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Keyword search: documents whose title or content match the query terms.
     * Returns [document_id, keyword_score] rows for hybrid re-ranking in the service layer.
     * plainto_tsquery is evaluated once via the FROM clause and reused for both filter and rank.
     */
    @Query(value = """
        SELECT id, ts_rank(search_vector, q) AS keyword_score
        FROM   documents, plainto_tsquery('english', :query) q
        WHERE  search_vector @@ q
        """, nativeQuery = true)
    List<Object[]> findDocumentIdsByKeyword(@Param("query") String query);
}
