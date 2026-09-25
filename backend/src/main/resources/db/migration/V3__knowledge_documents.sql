CREATE TABLE knowledge_documents (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    title varchar(160) NOT NULL,
    content_sha256 char(64) NOT NULL,
    embedding_model varchar(120) NOT NULL,
    chunk_count integer NOT NULL CHECK (chunk_count > 0),
    created_by varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_documents_org_sha_model_unique UNIQUE (organization_id, content_sha256, embedding_model),
    CONSTRAINT knowledge_documents_id_org_unique UNIQUE (id, organization_id)
);

CREATE INDEX knowledge_documents_org_created_idx
    ON knowledge_documents (organization_id, created_at DESC, id DESC);

CREATE TABLE document_chunks (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal >= 0),
    content text NOT NULL,
    embedding_model varchar(120) NOT NULL,
    embedding vector(768) NOT NULL,
    CONSTRAINT document_chunks_document_org_fk FOREIGN KEY (document_id, organization_id)
        REFERENCES knowledge_documents (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT document_chunks_document_ordinal_unique UNIQUE (document_id, ordinal)
);

-- Tenant filtering is applied before exact cosine ranking at this scale.
CREATE INDEX document_chunks_org_model_idx ON document_chunks (organization_id, embedding_model);
CREATE INDEX document_chunks_document_idx ON document_chunks (organization_id, document_id, ordinal);
