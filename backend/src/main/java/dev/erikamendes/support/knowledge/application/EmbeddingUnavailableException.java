package dev.erikamendes.support.knowledge.application;

public class EmbeddingUnavailableException extends RuntimeException {
    public EmbeddingUnavailableException() { super("Embedding service unavailable"); }
}
