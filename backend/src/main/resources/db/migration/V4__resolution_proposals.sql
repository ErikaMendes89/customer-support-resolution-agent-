CREATE TABLE resolution_proposals (
    id uuid PRIMARY KEY,
    case_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('READY_FOR_REVIEW', 'INSUFFICIENT_EVIDENCE')),
    answer text NOT NULL,
    generation_model varchar(120) NOT NULL,
    embedding_model varchar(120) NOT NULL,
    created_by varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT proposals_case_org_fk FOREIGN KEY (case_id, organization_id)
        REFERENCES support_cases (id, organization_id),
    CONSTRAINT proposals_id_org_unique UNIQUE (id, organization_id)
);
CREATE INDEX proposals_case_order_idx ON resolution_proposals (organization_id, case_id, created_at DESC, id DESC);

CREATE TABLE proposal_sources (
    proposal_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_key varchar(8) NOT NULL,
    document_id uuid NOT NULL,
    document_title varchar(160) NOT NULL,
    chunk_ordinal integer NOT NULL,
    content_snapshot text NOT NULL,
    similarity double precision NOT NULL,
    PRIMARY KEY (proposal_id, source_key),
    CONSTRAINT proposal_sources_proposal_org_fk FOREIGN KEY (proposal_id, organization_id)
        REFERENCES resolution_proposals (id, organization_id)
);
