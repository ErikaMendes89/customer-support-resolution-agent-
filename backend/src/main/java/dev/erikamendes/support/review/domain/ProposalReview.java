package dev.erikamendes.support.review.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProposalReview(UUID id, UUID proposalId, UUID caseId, Decision decision,
                             String finalAnswer, String note, String reviewedBy,
                             OffsetDateTime reviewedAt) {
    public enum Decision { APPROVED, EDITED, REJECTED }
}
