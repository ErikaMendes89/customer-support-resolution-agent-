package dev.erikamendes.support.knowledge.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KnowledgeDocument(UUID id, String title, String embeddingModel, int chunkCount,
                                String createdBy, OffsetDateTime createdAt) { }
