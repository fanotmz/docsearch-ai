CREATE TABLE docsearch_vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(1024)
);
