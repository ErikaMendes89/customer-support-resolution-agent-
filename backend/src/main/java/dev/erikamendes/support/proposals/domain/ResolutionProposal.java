package dev.erikamendes.support.proposals.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ResolutionProposal(UUID id, UUID caseId, String status, String answer,
                                 String generationModel, String embeddingModel, String createdBy,
                                 OffsetDateTime createdAt, List<Source> sources) {
    public record Source(String key, UUID documentId, String documentTitle, int chunkOrdinal,
                         String content, double similarity) { }
}
