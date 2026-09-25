CREATE TABLE proposal_generation_requests (
    organization_id uuid NOT NULL REFERENCES organizations(id),
    case_id uuid NOT NULL,
    request_key varchar(80) NOT NULL,
    state varchar(12) NOT NULL CHECK (state IN ('PROCESSING', 'COMPLETED')),
    proposal_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT generation_requests_pk PRIMARY KEY (organization_id, case_id, request_key),
    CONSTRAINT generation_requests_case_fk FOREIGN KEY (case_id, organization_id)
        REFERENCES support_cases (id, organization_id),
    CONSTRAINT generation_requests_proposal_fk FOREIGN KEY (proposal_id, case_id, organization_id)
        REFERENCES resolution_proposals (id, case_id, organization_id),
    CONSTRAINT generation_requests_state_check CHECK (
        (state = 'PROCESSING' AND proposal_id IS NULL)
        OR (state = 'COMPLETED' AND proposal_id IS NOT NULL)
    )
);
CREATE INDEX generation_requests_stale_idx ON proposal_generation_requests (created_at)
    WHERE state = 'PROCESSING';
