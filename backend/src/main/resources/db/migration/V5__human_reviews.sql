ALTER TABLE resolution_proposals
    ADD CONSTRAINT proposals_id_case_org_unique UNIQUE (id, case_id, organization_id);

CREATE TABLE proposal_reviews (
    id uuid PRIMARY KEY,
    proposal_id uuid NOT NULL,
    case_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    decision varchar(16) NOT NULL CHECK (decision IN ('APPROVED', 'EDITED', 'REJECTED')),
    final_answer text,
    note varchar(2000),
    reviewed_by varchar(120) NOT NULL,
    reviewed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT proposal_reviews_one_decision UNIQUE (proposal_id),
    CONSTRAINT proposal_reviews_proposal_case_org_fk FOREIGN KEY (proposal_id, case_id, organization_id)
        REFERENCES resolution_proposals (id, case_id, organization_id),
    CONSTRAINT proposal_reviews_answer_check CHECK (
        (decision IN ('APPROVED', 'EDITED') AND final_answer IS NOT NULL AND length(trim(final_answer)) > 0)
        OR (decision = 'REJECTED' AND final_answer IS NULL AND note IS NOT NULL AND length(trim(note)) > 0)
    )
);
CREATE INDEX proposal_reviews_case_history_idx ON proposal_reviews (organization_id, case_id, reviewed_at DESC, id DESC);
