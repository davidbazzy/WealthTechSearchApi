package com.baz.searchapi.repository;

import com.baz.searchapi.model.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {
}
