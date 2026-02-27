package com.baz.searchapi.repository;

import com.baz.searchapi.model.entity.Chunk;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    /**
     * Returns the top-K documents nearest to queryEmbedding by cosine distance.
     * Aggregates chunk scores per document in a single DB round-trip using the HNSW index.
     * Returns [document_id, score] rows — no heap loading of embeddings.
     */
    @Query(value = """
        SELECT document_id, MAX(1.0 - dist) AS score
        FROM (
            SELECT document_id, (embedding <=> CAST(:queryVec AS vector)) AS dist
            FROM   chunks
            ORDER  BY dist
            LIMIT  1000
        ) nearest
        GROUP  BY document_id
        ORDER  BY score DESC
        """, nativeQuery = true)
    List<Object[]> findTopDocumentsByEmbedding(@Param("queryVec") String queryVec);
}
