CREATE TABLE documents (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    page_count INTEGER NOT NULL DEFAULT 0 CHECK (page_count >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    error_code VARCHAR(64),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE TABLE document_pages (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    source VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    UNIQUE (document_id, page_number)
);

CREATE INDEX ingestion_jobs_document_id_idx ON ingestion_jobs (document_id);
CREATE INDEX document_pages_document_id_idx ON document_pages (document_id);
