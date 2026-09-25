package dev.erikamendes.support.cases.domain;

import java.time.OffsetDateTime;

public record CaseEvent(long id, String eventType, CaseStatus fromStatus, CaseStatus toStatus,
                        String note, String actor, OffsetDateTime occurredAt) { }
