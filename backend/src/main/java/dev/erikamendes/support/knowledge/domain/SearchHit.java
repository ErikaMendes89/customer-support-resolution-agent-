package dev.erikamendes.support.knowledge.domain;

import java.util.UUID;

public record SearchHit(UUID documentId, String documentTitle, int chunkOrdinal,
                        String content, double similarity) { }
