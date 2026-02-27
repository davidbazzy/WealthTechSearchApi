-- V1__init.sql
-- Initial schema: pgvector extension, all tables, indexes

-- Enable pgvector (safe to run multiple times)
CREATE EXTENSION IF NOT EXISTS vector;

-- -------------------------------------------------------------------------
-- Clients
-- -------------------------------------------------------------------------
CREATE TABLE clients (
    id          UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  TEXT  NOT NULL,
    last_name   TEXT  NOT NULL,
    email       TEXT  NOT NULL UNIQUE,
    description TEXT
);

-- Social links (@ElementCollection — separate table, one row per link)
CREATE TABLE client_social_links (
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    link      TEXT NOT NULL
);

-- -------------------------------------------------------------------------
-- Documents
-- -------------------------------------------------------------------------
CREATE TABLE documents (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id  UUID        NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    title      TEXT        NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Generated tsvector column across title + content
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(title,   '') || ' ' ||
            coalesce(content, ''))
    ) STORED,

    UNIQUE (client_id, title)
);

CREATE INDEX idx_documents_search ON documents USING GIN (search_vector);
CREATE INDEX idx_documents_client ON documents (client_id);

-- -------------------------------------------------------------------------
-- Chunks (semantic search units)
-- -------------------------------------------------------------------------
CREATE TABLE chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT  NOT NULL,
    text        TEXT NOT NULL,

    -- 384 dimensions: all-MiniLM-L6-v2 output size
    embedding   vector(384) NOT NULL
);

-- HNSW index for approximate nearest-neighbour search.
-- vector_cosine_ops: correct for L2-normalised embeddings from all-MiniLM-L6-v2.
-- m=16, ef_construction=64: good defaults balancing recall and build time.
-- No training step required (unlike IVFFlat) — safe to build on an empty table.
CREATE INDEX idx_chunks_embedding ON chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_chunks_document ON chunks (document_id);
