CREATE TABLE organizations (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE
);

-- Fictitious organizations for the two local demonstration accounts.
INSERT INTO organizations (id, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Aurora Demo'),
    ('22222222-2222-2222-2222-222222222222', 'Horizonte Demo');

CREATE TABLE support_cases (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    title varchar(160) NOT NULL,
    description text NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'NEEDS_INFORMATION', 'RESOLVED', 'CLOSED')),
    created_by varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT support_cases_id_org_unique UNIQUE (id, organization_id)
);

CREATE INDEX support_cases_org_created_idx ON support_cases (organization_id, created_at DESC, id DESC);

CREATE TABLE case_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    event_type varchar(32) NOT NULL CHECK (event_type IN ('CREATED', 'STATUS_CHANGED')),
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    note varchar(2000),
    actor varchar(120) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT case_events_case_org_fk FOREIGN KEY (case_id, organization_id)
        REFERENCES support_cases (id, organization_id)
);

CREATE INDEX case_events_case_org_order_idx ON case_events (organization_id, case_id, id);
