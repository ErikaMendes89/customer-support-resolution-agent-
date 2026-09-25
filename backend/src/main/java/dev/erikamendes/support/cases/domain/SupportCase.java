package dev.erikamendes.support.cases.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupportCase(UUID id, UUID organizationId, String title, String description,
                          CaseStatus status, String createdBy, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt) { }
