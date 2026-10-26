-- EMBEDDING_DIMENSION is intentionally a Flyway placeholder.  A different model
-- dimension is a reindex operation, not a runtime environment switch.
ALTER TABLE document_chunks
    ALTER COLUMN embedding TYPE vector(${embedding_dimension})
    USING embedding::vector(${embedding_dimension});

CREATE INDEX document_chunks_embedding_idx
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
